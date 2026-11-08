package com.karapp.drawguidance.server.billing;

import java.util.List;

/**
 * v39 plan kimlikleri ve özellik (erişim hakkı) anahtarları. Anahtarlar uygulamadakiyle birebir aynıdır; Ücretsiz
 * planda hiçbiri yoktur, Premium planda hepsi vardır ({@code ads.none}: reklamsız).
 */
public final class Plans {
    private Plans() {}

    public static final String FREE = "free";
    public static final String PREMIUM = "premium";

    /** Premium'un açtığı bütün özellikler (sıra = gösterim sırası). */
    public static final List<String> FEATURE_KEYS = List.of(
            "art.image_bg_removal",
            "art.image_effects_advanced",
            "art.shape_suggest_advanced",
            "art.shapes_advanced",
            "notes.image_bg_removal",
            "notes.image_effects_advanced",
            "notes.diagrams_advanced",
            "notes.shape_suggest_advanced",
            "comic.image_bg_removal",
            "comic.effects_advanced",
            "comic.characters_advanced",
            "comic.objects_suggest_advanced",
            "comic.effects",
            "comic.characters",
            "comic.backgrounds",
            "comic.templates",
            "comic.shapes_advanced",
            "model3d.suggest",
            // v44 3B modeli GLB olarak dışa aktarma ve paylaşma
            "model3d.export",
            // v40 çocuk modu çizim görevinin son 15 seviyesi (36–50)
            "kids.challenge_advanced",
            "ads.none");
}
