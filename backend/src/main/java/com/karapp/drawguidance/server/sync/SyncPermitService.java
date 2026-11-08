package com.karapp.drawguidance.server.sync;

import com.karapp.drawguidance.server.catalog.CatalogService;
import com.karapp.drawguidance.server.common.Stores;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/**
 * v40 içerik eşitleme izni. Girişli kullanıcıya ilk izin isteğinde kalıcı bir sıra numarası ({@code sync_groups},
 * {@code _id} = Firebase uid, {@code seq}) atanır; sayaç {@code counters} koleksiyonundaki {@code sync_seq} belgesidir
 * (atomik artırma). Böylece grup ({@code seq / groupSize}) kullanıcının ilk eşitlemesinin sırasına göre belirlenir ve
 * değişmez. Kimliksiz cihazların dilimi cihaz kimliğinin özetinden hesaplanır (kayıt yazılmaz).
 */
@Service
public class SyncPermitService {
    static final String COUNTER_ID = "sync_seq";

    /** İzin yanıtı ({@code GET /api/v1/sync/permit}). {@code group} kimliksiz cihazda {@code null}. */
    public record Permit(boolean allowed, Instant slotStart, Instant slotEnd, Instant nextSlotStart, long contentVersion,
                         int slot, Long group, long slotLengthSeconds, Instant serverTime) {}

    private final MongoTemplate mongo;
    private final CatalogService catalog;
    private final SyncPermitProperties props;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public SyncPermitService(MongoTemplate mongo, CatalogService catalog, SyncPermitProperties props) {
        this(mongo, catalog, props, Clock.systemUTC());
    }

    SyncPermitService(MongoTemplate mongo, CatalogService catalog, SyncPermitProperties props, Clock clock) {
        this.mongo = mongo;
        this.catalog = catalog;
        this.props = props;
        this.clock = clock;
    }

    public Permit forUser(String uid) {
        long group = SyncSlots.group(seqOf(uid), props.groupSize());
        return permit(SyncSlots.slotOfGroup(group, props.slots()), group);
    }

    public Permit forDevice(String deviceId) {
        return permit(SyncSlots.slotOfDevice(deviceId, props.slots()), null);
    }

    private Permit permit(int slot, Long group) {
        Instant now = clock.instant();
        SyncSlots.Window w = SyncSlots.window(slot, now, props);
        return new Permit(w.allowed(), w.slotStart(), w.slotEnd(), w.nextSlotStart(), catalog.meta().versionOrZero(), slot, group,
                props.slotLength().toSeconds(), now);
    }

    /** Kullanıcının sıra numarası (yoksa sayaçtan atanır; eşzamanlı ilk isteklerde ilk yazılan kalır). */
    long seqOf(String uid) {
        var groups = mongo.getCollection(Stores.SYNC_GROUPS);
        Document existing = groups.find(Filters.eq("_id", uid)).first();
        if (existing != null) return ((Number) existing.get("seq")).longValue();
        Document counter = mongo.getCollection(Stores.COUNTERS).findOneAndUpdate(Filters.eq("_id", COUNTER_ID), Updates.inc("value", 1L),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        long seq = ((Number) counter.get("value")).longValue() - 1;
        try {
            groups.insertOne(new Document("_id", uid).append("seq", seq).append("createdAt", Date.from(clock.instant())));
            return seq;
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) throw e;
            Document won = groups.find(Filters.eq("_id", uid)).first();
            return won == null ? seq : ((Number) won.get("seq")).longValue();
        }
    }
}
