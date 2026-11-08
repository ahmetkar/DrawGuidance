package com.karapp.drawguidance.server.catalog;

import com.karapp.drawguidance.server.common.Stores;

/**
 * Katalog bölümleri. Sunucu öğelerin içeriğini yorumlamaz; yalnız anahtarı bilir ({@code _id} olarak saklanır).
 *
 * @param name        tohum dosyasındaki dizi adı ve manifestteki {@code counts} anahtarı
 * @param collection  MongoDB koleksiyonu
 * @param keyField    öğenin anahtar alanı (Template.key, Vector.id, ModeNet.mode)
 */
public enum CatalogKind {
    TEMPLATES("templates", Stores.CATALOG_TEMPLATES, "key"),
    VECTORS("vectors", Stores.CATALOG_VECTORS, "id"),
    MODE_NETS("modeNets", Stores.CATALOG_MODENETS, "mode");

    private final String name;
    private final String collection;
    private final String keyField;

    CatalogKind(String name, String collection, String keyField) {
        this.name = name;
        this.collection = collection;
        this.keyField = keyField;
    }

    public String fileName() { return name; }
    public String collection() { return collection; }
    public String keyField() { return keyField; }
}
