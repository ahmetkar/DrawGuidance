package com.karapp.drawguidance.content.library.kids

import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.P

/*
 * v34 "Çocuklar için çizim": çocuk çizgi filmlerinde en sık görülen türlerden (masal, macera, uzay, deniz, süper
 * kahraman, sevimli canavar…) esinlenen ÖZGÜN boyama çizimleri. Hiçbiri tanınmış bir karakteri, markayı ya da diziyi
 * taklit etmez: adlar genel, biçimler sade ve bu uygulamaya özgü. Kurallar v33 çizimleriyle aynı: kalın kenar, her
 * parça beyaz kapalı yol; üst üste binen parçalar birbirinden kesilir (gizli çizgi yok), her bölge dokunarak boyanır.
 */

// =====================================================================================================================
// Çizgi film karakterleri
// =====================================================================================================================

internal object KidCharacters {

    val shapes: List<LibShape> by lazy {
        listOf(
            dragon(), princess(), knight(), pirate(), alien(), hero(), fairy(), wizard(), mermaid(), unicorn(),
            monster(), ghost(), hatchling(), frogPrince(),
        )
    }

    private fun dragon() = kid("baby_dragon", "Yavru ejderha", 110f, 100f, listOf("ejderha", "yavru ejderha", "masal", "kanat", "dragon", "baby dragon")) {
        val head = circle(44f, 32f, 20f)
        val body = blob(42f, 52f, 60f, 54f, 70f, 66f, 70f, 84f, 56f, 95f, 32f, 95f, 20f, 84f, 22f, 64f)
        val wing = blob(60f, 56f, 78f, 30f, 98f, 34f, 92f, 50f, 100f, 56f, 84f, 64f, 70f, 70f)
        val tail = tube(7f, 3f, 64f, 88f, 82f, 92f, 94f, 84f)
        val tip = rpoly(1.2f, 90f, 86f, 104f, 74f, 102f, 92f)
        val footL = oval(32f, 94f, 9f, 5f)
        val footR = oval(56f, 94f, 9f, 5f)
        val hornL = rpoly(1.5f, 30f, 20f, 26f, 4f, 40f, 15f)
        val hornR = mirror(hornL, 44f)
        part(cut(wing, body, head))
        part(cut(tail, body, tip))
        part(tip)
        part(cut(hornL, head)); part(cut(hornR, head))
        val b = cut(body, head, footL, footR)
        part(b)
        part(clip(oval(46f, 77f, 12f, 13f), b))
        part(footL); part(footR)
        part(head)
        part(oval(44f, 42f, 10f, 7f))
        dot(40f, 41f, 1.4f); dot(48f, 41f, 1.4f)
        eye(36f, 28f, 3.2f); eye(52f, 28f, 3.2f)
    }

    private fun princess() = kid("princess", "Prenses", 90f, 110f, listOf("prenses", "masal", "taç", "elbise", "princess", "fairy tale")) {
        val head = circle(45f, 32f, 14f)
        val crown = rpoly(0.8f, 34f, 22f, 33f, 8f, 39f, 15f, 45f, 5f, 51f, 15f, 57f, 8f, 56f, 22f)
        val hair = blob(26f, 34f, 30f, 16f, 45f, 12f, 60f, 16f, 64f, 34f, 66f, 58f, 56f, 56f, 34f, 56f, 24f, 58f)
        val bodice = rrect(35f, 46f, 20f, 22f, 5f)
        val skirt = rpoly(4f, 34f, 64f, 56f, 64f, 82f, 106f, 8f, 106f)
        bigParts(cutAll(hair, head, crown, bodice))
        val sk = cut(skirt, bodice)
        part(sk)
        strokeInside(poly(6f, 92f, 24f, 87f, 45f, 93f, 66f, 87f, 84f, 92f), sk)
        part(bodice)
        part(star(45f, 57f, 7f, 3.2f))
        part(cut(head, crown))
        part(crown)
        dot(45f, 15f, 1.6f)
        eye(40f, 33f, 2.4f); eye(50f, 33f, 2.4f)
        smile(45f, 39f, 7f, 1.4f)
    }

    private fun knight() = kid("knight", "Şövalye", 100f, 110f, listOf("şövalye", "zırh", "kalkan", "kılıç", "masal", "knight", "armor")) {
        val plume = blob(46f, 16f, 50f, 4f, 62f, 2f, 72f, 8f, 64f, 14f, 54f, 18f)
        val helmet = shape { moveTo(32f, 48f); lineTo(32f, 30f); arcTo(50f, 30f, 18f, 16f, 180f, 180f); lineTo(68f, 48f) }
        val body = rrect(30f, 46f, 40f, 38f, 7f)
        val legL = rrect(34f, 80f, 13f, 26f, 3f)
        val legR = rrect(53f, 80f, 13f, 26f, 3f)
        val shield = shape { moveTo(12f, 52f); lineTo(42f, 52f); lineTo(42f, 72f); quadTo(42f, 90f, 27f, 98f); quadTo(12f, 90f, 12f, 72f) }
        val blade = rpoly(0.8f, 76f, 30f, 79f, 22f, 82f, 30f, 82f, 68f, 76f, 68f)
        val guard = rrect(72f, 66f, 16f, 5f, 2f)
        val grip = rect(76f, 70f, 6f, 14f)
        part(cut(plume, helmet))
        part(cut(helmet, body))
        part(rrect(38f, 28f, 24f, 7f, 3.5f))
        ink(rrect(41f, 30.5f, 18f, 2f, 1f))
        part(cut(legL, body, shield)); part(cut(legR, body))
        part(cut(body, shield))
        part(shield)
        part(star(27f, 70f, 8f, 3.6f))
        part(cut(blade, guard)); part(guard); part(cut(grip, guard))
    }

    private fun pirate() = kid("pirate", "Korsan", 100f, 110f, listOf("korsan", "şapka", "gemi", "macera", "pirate", "adventure")) {
        val head = circle(50f, 40f, 16f)
        val hat = shape { moveTo(18f, 30f); quadTo(50f, 0f, 82f, 30f); lineTo(80f, 36f); quadTo(50f, 20f, 20f, 36f) }
        val body = rrect(30f, 54f, 40f, 36f, 8f)
        val legL = rrect(35f, 86f, 12f, 20f, 3f)
        val legR = rrect(53f, 86f, 12f, 20f, 3f)
        part(cut(legL, body)); part(cut(legR, body))
        val b = cut(body, head)
        part(b)
        for (y in floatArrayOf(63f, 71f)) strokeInside(listOf(P(20f, y), P(80f, y)), b)
        val buckle = rrect(45f, 76f, 10f, 10f, 2f)
        parts(cutAll(rect(31f, 78f, 38f, 6f), buckle))
        part(buckle)
        part(cut(head, hat))
        part(hat)
        part(circle(50f, 22f, 4f))
        ink(oval(56f, 40f, 4f, 3.5f))
        eye(44f, 40f, 2.6f)
        smile(50f, 47f, 9f, 1.5f)
    }

    private fun alien() = kid("alien_buddy", "Sevimli uzaylı", 100f, 100f, listOf("uzaylı", "anten", "uzay", "sevimli", "alien", "space")) {
        val head = oval(50f, 42f, 30f, 22f)
        val body = oval(50f, 78f, 17f, 17f)
        val armL = capsule(36f, 74f, 20f, 64f, 4.5f)
        val armR = mirror(armL, 50f)
        val ballL = circle(30f, 9f, 5.5f)
        val ballR = circle(70f, 9f, 5.5f)
        strokeOutside(listOf(P(40f, 24f), P(31f, 10f)), ballL, head)
        strokeOutside(listOf(P(60f, 24f), P(69f, 10f)), ballR, head)
        part(cut(armL, body)); part(cut(armR, body))
        val b = cut(body, head)
        part(b)
        part(clip(oval(50f, 82f, 8f, 7f), b))
        part(ballL); part(ballR)
        part(head)
        part(circle(35f, 40f, 7f)); part(circle(50f, 35f, 8f)); part(circle(65f, 40f, 7f))
        eye(35f, 41f, 3f); eye(50f, 36f, 3.4f); eye(65f, 41f, 3f)
        smile(50f, 52f, 12f, 2f)
    }

    private fun hero() = kid("hero_kid", "Süper kahraman çocuk", 100f, 110f, listOf("süper kahraman", "pelerin", "maske", "kahraman", "superhero", "hero")) {
        val cape = rpoly(3f, 32f, 44f, 68f, 44f, 86f, 102f, 14f, 102f)
        val head = circle(50f, 28f, 15f)
        val body = rrect(36f, 42f, 28f, 36f, 8f)
        val armL = capsule(39f, 50f, 20f, 26f, 5f)
        val armR = capsule(61f, 52f, 76f, 72f, 5f)
        val legL = rrect(37f, 74f, 13f, 32f, 4f)
        val legR = rrect(50f, 74f, 13f, 32f, 4f)
        parts(cutAll(cape, head, body, armL, armR, legL, legR))
        part(cut(armL, body, head)); part(cut(armR, body))
        part(cut(legL, body)); part(cut(legR, body, legL))
        part(body)
        part(star(50f, 58f, 8f, 3.6f))
        part(head)
        part(clip(rrect(33f, 21f, 34f, 10f, 5f), head))
        eye(44f, 26f, 2.2f); eye(56f, 26f, 2.2f)
        smile(50f, 35f, 8f, 1.4f)
    }

    private fun fairy() = kid("fairy", "Peri", 100f, 100f, listOf("peri", "kanat", "sihir", "masal", "fairy", "magic")) {
        val head = circle(50f, 26f, 11f)
        val bun = circle(50f, 13f, 6f)
        val dress = rpoly(3f, 50f, 36f, 68f, 80f, 32f, 80f)
        val wUL = oval(30f, 40f, 18f, 11f, -30f)
        val wUR = mirror(wUL, 50f)
        val wLL = oval(34f, 63f, 12f, 8f, 30f)
        val wLR = mirror(wLL, 50f)
        val wand = star(84f, 26f, 8f, 3.6f)
        part(cut(wLL, wUL, dress)); part(cut(wLR, wUR, dress))
        part(cut(wUL, dress, head)); part(cut(wUR, dress, head))
        strokeOutside(listOf(P(63f, 54f), P(80f, 32f)), dress, wand)
        part(wand)
        part(dress)
        seg(45f, 80f, 44f, 92f); seg(55f, 80f, 56f, 92f)
        part(cut(bun, head))
        part(head)
        eye(46f, 26f, 2f); eye(54f, 26f, 2f)
        smile(50f, 30f, 5f, 1f)
    }

    private fun wizard() = kid("wizard", "Büyücü", 100f, 110f, listOf("büyücü", "sihirbaz", "şapka", "asa", "sihir", "wizard", "magic")) {
        val hat = rpoly(2f, 50f, 2f, 70f, 38f, 30f, 38f)
        val brim = rrect(20f, 34f, 60f, 8f, 4f)
        val face = circle(50f, 50f, 12f)
        val beard = blob(36f, 52f, 50f, 56f, 64f, 52f, 66f, 66f, 50f, 82f, 34f, 66f)
        val robe = rpoly(4f, 34f, 60f, 66f, 60f, 82f, 106f, 18f, 106f)
        val staff = rrect(85.5f, 34f, 5f, 72f, 2f)
        val orb = circle(88f, 28f, 8f)
        val h = cut(hat, brim)
        part(h)
        part(clip(star(50f, 25f, 6f, 2.6f), h))
        part(brim)
        val r = cut(robe, beard, face)
        part(r)
        part(clip(star(38f, 92f, 6.5f, 3f), r)); part(clip(star(62f, 86f, 6.5f, 3f), r))
        part(cut(face, brim, beard))
        part(beard)
        part(cut(staff, orb)); part(orb)
        eye(45f, 47f, 2.2f); eye(55f, 47f, 2.2f)
    }

    private fun mermaid() = kid("mermaid", "Deniz kızı", 100f, 110f, listOf("deniz kızı", "kuyruk", "deniz", "masal", "mermaid", "sea")) {
        val head = circle(50f, 26f, 12f)
        val hair = blob(34f, 22f, 40f, 10f, 50f, 7f, 62f, 12f, 68f, 28f, 70f, 50f, 62f, 62f, 58f, 44f, 42f, 44f, 38f, 62f, 30f, 50f)
        val clipStar = star(64f, 15f, 5.5f, 2.4f)
        val torso = rrect(40f, 38f, 20f, 24f, 7f)
        val tail = blob(40f, 58f, 60f, 58f, 64f, 74f, 58f, 92f, 50f, 98f, 42f, 92f, 36f, 74f)
        val fin = shape { moveTo(50f, 92f); quadTo(64f, 92f, 78f, 106f); quadTo(60f, 104f, 50f, 100f); quadTo(40f, 104f, 22f, 106f); quadTo(36f, 92f, 50f, 92f) }
        bigParts(cutAll(hair, head, torso, clipStar, tail, fin))
        part(clipStar)
        val t = cut(tail, torso, fin)
        part(t)
        strokeInside(arc(44f, 70f, 4f, 3f, 20f, 140f), t)
        strokeInside(arc(56f, 70f, 4f, 3f, 20f, 140f), t)
        strokeInside(arc(50f, 81f, 4f, 3f, 20f, 140f), t)
        part(fin)
        part(torso)
        part(star(50f, 48f, 6.5f, 3f))
        part(head)
        eye(45f, 26f, 2.2f); eye(55f, 26f, 2.2f)
        smile(50f, 31f, 6f, 1.2f)
    }

    private fun unicorn() = kid("unicorn", "Tek boynuzlu at", 120f, 100f, listOf("tek boynuzlu at", "boynuz", "masal", "at", "unicorn", "magic")) {
        val body = oval(58f, 58f, 30f, 17f)
        val legs = listOf(rrect(34f, 62f, 9f, 32f, 3f), rrect(47f, 64f, 9f, 30f, 3f), rrect(64f, 64f, 9f, 30f, 3f), rrect(77f, 62f, 9f, 32f, 3f))
        val neck = rpoly(3f, 76f, 52f, 84f, 26f, 98f, 30f, 92f, 58f)
        val head = oval(98f, 30f, 14f, 10f, 25f)
        val horn = rpoly(0.8f, 94f, 22f, 104f, 2f, 102f, 24f)
        val mane = blob(78f, 22f, 88f, 14f, 92f, 20f, 84f, 38f, 78f, 54f, 70f, 44f)
        val tail = blob(30f, 52f, 20f, 50f, 8f, 60f, 6f, 78f, 14f, 70f, 22f, 64f, 30f, 62f)
        part(cut(tail, body))
        legs.forEach { part(cut(it, body)) }
        part(cut(mane, neck, head, body))
        part(cut(neck, body, head))
        part(body)
        part(cut(head, horn))
        part(horn)
        eye(100f, 28f, 2.2f)
        dot(108f, 35f, 1.2f)
    }

    private fun monster() = kid("cute_monster", "Sevimli canavar", 100f, 100f, listOf("canavar", "sevimli canavar", "tüylü", "boynuz", "monster", "cute")) {
        val body = puff(50f, 54f, 34f, 34f, 14, 0.1f)
        val hornL = rpoly(2f, 30f, 32f, 24f, 8f, 42f, 24f)
        val hornR = mirror(hornL, 50f)
        val footL = oval(36f, 90f, 11f, 6f)
        val footR = oval(64f, 90f, 11f, 6f)
        part(cut(hornL, body)); part(cut(hornR, body))
        part(cut(body, footL, footR))
        part(footL); part(footR)
        part(circle(50f, 44f, 13f))
        eye(50f, 46f, 6f)
        part(rrect(34f, 62f, 32f, 12f, 6f))
        part(poly(40f, 63f, 44f, 70f, 48f, 63f)); part(poly(52f, 63f, 56f, 70f, 60f, 63f))
    }

    private fun ghost() = kid("friendly_ghost", "Sevimli hayalet", 100f, 100f, listOf("hayalet", "sevimli hayalet", "masal", "ghost", "boo")) {
        val body = shape {
            moveTo(22f, 88f); lineTo(22f, 46f); cubicTo(22f, 12f, 78f, 12f, 78f, 46f); lineTo(78f, 88f)
            quadTo(72f, 96f, 65f, 88f); quadTo(57f, 96f, 50f, 88f); quadTo(43f, 96f, 36f, 88f); quadTo(29f, 96f, 22f, 88f)
        }
        val armL = oval(18f, 58f, 9f, 5.5f, -30f)
        val armR = mirror(armL, 50f)
        part(cut(armL, body)); part(cut(armR, body))
        part(body)
        ink(oval(41f, 44f, 4f, 6f)); ink(oval(59f, 44f, 4f, 6f))
        ink(oval(50f, 60f, 5f, 6f))
        part(circle(32f, 56f, 4.5f)); part(circle(68f, 56f, 4.5f))
        part(star(86f, 16f, 9f, 4f))
        part(star(14f, 20f, 6f, 2.6f))
    }

    private fun hatchling() = kid("hatchling", "Yumurtadan çıkan dinozor", 100f, 100f, listOf("dinozor", "yumurta", "yavru", "dinosaur", "egg", "hatchling")) {
        val egg = KidPen.rounded(shape {
            moveTo(20f, 58f); lineTo(27f, 51f); lineTo(35f, 58f); lineTo(43f, 51f); lineTo(51f, 58f); lineTo(59f, 51f); lineTo(67f, 58f)
            lineTo(74f, 51f); lineTo(80f, 57f)
            cubicTo(82f, 84f, 66f, 97f, 50f, 97f); cubicTo(34f, 97f, 18f, 84f, 20f, 58f)
        }, 1.2f)
        val head = oval(50f, 38f, 21f, 17f)
        val cap = shape {
            moveTo(32f, 28f); lineTo(41f, 31f); lineTo(50f, 27f); lineTo(59f, 31f); lineTo(68f, 28f)
            cubicTo(66f, 8f, 34f, 8f, 32f, 28f)
        }
        val armL = oval(21f, 67f, 7f, 4.5f, -35f)
        val armR = mirror(armL, 50f)
        bigParts(cutAll(head, egg, cap))
        part(cap)
        part(cut(egg, armL, armR))
        part(armL); part(armR)
        part(oval(40f, 78f, 6f, 4f)); part(oval(60f, 82f, 5f, 3.5f)); part(oval(54f, 69f, 4f, 3f))
        eye(43f, 37f, 2.8f); eye(57f, 37f, 2.8f)
        smile(50f, 44f, 8f, 1.4f)
    }

    private fun frogPrince() = kid("frog_prince", "Kurbağa prens", 100f, 100f, listOf("kurbağa", "prens", "taç", "masal", "nilüfer", "frog", "prince")) {
        val pad = cut(oval(50f, 86f, 44f, 11f), poly(50f, 86f, 66f, 70f, 78f, 74f))
        val body = oval(50f, 64f, 27f, 20f)
        val eyeL = circle(36f, 44f, 10f)
        val eyeR = circle(64f, 44f, 10f)
        val crown = rpoly(0.8f, 42f, 40f, 43f, 26f, 47f, 32f, 50f, 22f, 53f, 32f, 57f, 26f, 58f, 40f)
        val footL = oval(28f, 84f, 10f, 5f)
        val footR = oval(72f, 84f, 10f, 5f)
        parts(cutAll(pad, body, footL, footR))
        part(cut(body, eyeL, eyeR, crown, footL, footR))
        part(cut(eyeL, crown)); part(cut(eyeR, crown))
        part(crown)
        part(footL); part(footR)
        part(oval(50f, 70f, 14f, 10f))
        eye(37f, 45f, 4f); eye(63f, 45f, 4f)
        smile(50f, 56f, 16f, 2f)
    }
}

// =====================================================================================================================
// Masal ve macera yerleri
// =====================================================================================================================

internal object KidPlaces {

    val shapes: List<LibShape> by lazy {
        listOf(castle(), pirateShip(), treehouse(), candyHouse(), barn(), igloo(), circusTent(), lighthouse(), jungleHut(), dragonCave(), spaceStation())
    }

    private fun KidPen.arch(x0: Float, x1: Float, top: Float, bottom: Float): Poly {
        val r = (x1 - x0) / 2f
        return shape { moveTo(x0, bottom); lineTo(x0, top + r); arcTo(x0 + r, top + r, r, r, 180f, 180f); lineTo(x1, bottom) }
    }

    private fun castle() = kid("castle", "Şato", 120f, 100f, listOf("şato", "kale", "kule", "masal", "castle", "fairy tale")) {
        val wall = poly(24f, 96f, 24f, 44f, 34f, 44f, 34f, 38f, 44f, 38f, 44f, 44f, 54f, 44f, 54f, 38f, 66f, 38f, 66f, 44f, 76f, 44f, 76f, 38f, 86f, 38f, 86f, 44f, 96f, 44f, 96f, 96f)
        val towerL = rect(8f, 32f, 22f, 64f)
        val towerR = rect(90f, 32f, 22f, 64f)
        val roofL = rpoly(1.5f, 5f, 36f, 19f, 12f, 33f, 36f)
        val roofR = rpoly(1.5f, 87f, 36f, 101f, 12f, 115f, 36f)
        seg(19f, 12f, 19f, 2f); seg(101f, 12f, 101f, 2f)
        part(poly(19.5f, 2f, 30f, 5.5f, 19.5f, 9f)); part(poly(101.5f, 2f, 112f, 5.5f, 101.5f, 9f))
        part(cut(wall, towerL, towerR))
        part(cut(towerL, roofL)); part(cut(towerR, roofR))
        part(roofL); part(roofR)
        part(arch(50f, 70f, 70f, 96f))
        part(arch(14f, 24f, 50f, 64f)); part(arch(96f, 106f, 50f, 64f))
        part(arch(55f, 65f, 50f, 62f))
    }

    private fun pirateShip() = kid("pirate_ship", "Korsan gemisi", 120f, 100f, listOf("korsan gemisi", "gemi", "yelken", "deniz", "pirate ship", "ship")) {
        val hull = rpoly(2f, 8f, 60f, 112f, 60f, 98f, 90f, 22f, 90f)
        val sail = shape { moveTo(34f, 16f); quadTo(60f, 24f, 86f, 16f); lineTo(88f, 48f); quadTo(60f, 56f, 32f, 48f) }
        val mast = rect(57f, 6f, 6f, 56f)
        val flag = rpoly(0.8f, 63f, 6f, 82f, 10f, 63f, 15f)
        val waves = shape {
            moveTo(2f, 98f); lineTo(2f, 84f); quadTo(12f, 76f, 22f, 84f); quadTo(32f, 92f, 42f, 84f); quadTo(52f, 76f, 62f, 84f)
            quadTo(72f, 92f, 82f, 84f); quadTo(92f, 76f, 102f, 84f); quadTo(112f, 92f, 118f, 84f); lineTo(118f, 98f)
        }
        parts(cutAll(mast, sail, hull))
        part(flag)
        part(sail)
        part(circle(60f, 34f, 7f))
        part(cut(hull, waves))
        part(circle(36f, 70f, 4.5f)); part(circle(60f, 70f, 4.5f)); part(circle(84f, 70f, 4.5f))
        part(waves)
    }

    private fun treehouse() = kid("treehouse", "Ağaç ev", 100f, 110f, listOf("ağaç ev", "ağaç", "ev", "macera", "treehouse", "tree")) {
        val leaves = cloud(50f, 30f, 92f, 50f)
        val roof = rpoly(2f, 24f, 50f, 50f, 30f, 76f, 50f)
        val house = rect(30f, 50f, 40f, 26f)
        val trunk = rect(42f, 44f, 16f, 62f)
        val ground = grass(4f, 96f, 108f, 96f, 102f, 12)
        parts(cutAll(trunk, leaves, house, ground))
        part(cut(leaves, roof, house))
        part(circle(20f, 30f, 4f)); part(circle(78f, 24f, 4f)); part(circle(46f, 16f, 4f))
        part(roof)
        part(house)
        part(rect(35f, 56f, 10f, 10f)); part(rect(54f, 57f, 10f, 17f))
        seg(66f, 76f, 66f, 96f); seg(73f, 76f, 73f, 96f)
        for (y in floatArrayOf(82f, 88f, 94f)) seg(66f, y, 73f, y)
        part(ground)
    }

    private fun candyHouse() = kid("candy_house", "Şeker ev", 100f, 100f, listOf("şeker ev", "kurabiye ev", "masal", "şeker", "candy house", "gingerbread")) {
        val roof = shape {
            moveTo(8f, 50f); lineTo(50f, 12f); lineTo(92f, 50f)
            quadTo(85f, 57f, 78f, 50f); quadTo(71f, 57f, 64f, 50f); quadTo(57f, 57f, 50f, 50f); quadTo(43f, 57f, 36f, 50f)
            quadTo(29f, 57f, 22f, 50f); quadTo(15f, 57f, 8f, 50f)
        }
        val walls = rect(16f, 46f, 68f, 48f)
        val candy = circle(92f, 58f, 7f)
        part(cut(walls, roof))
        part(roof)
        part(circle(50f, 30f, 5f)); part(circle(36f, 42f, 3.5f)); part(circle(64f, 42f, 3.5f))
        part(arch(42f, 58f, 70f, 94f))
        part(circle(28f, 70f, 6f)); part(circle(72f, 70f, 6f))
        part(cut(rect(90.5f, 62f, 3f, 32f), candy))
        part(candy)
    }

    private fun barn() = kid("barn", "Ahır", 110f, 100f, listOf("ahır", "çiftlik", "ambar", "hayvan", "farm", "barn")) {
        val barn = rpoly(1.5f, 18f, 96f, 18f, 42f, 30f, 22f, 55f, 10f, 80f, 22f, 92f, 42f, 92f, 96f)
        val trim = poly(18f, 42f, 30f, 22f, 55f, 10f, 80f, 22f, 92f, 42f, 84f, 42f, 74f, 28f, 55f, 19f, 36f, 28f, 26f, 42f)
        val t = clip(trim, barn)
        part(cut(barn, t))
        part(t)
        val door = rect(38f, 60f, 34f, 36f)
        part(door)
        seg(38f, 60f, 72f, 96f); seg(72f, 60f, 38f, 96f)
        part(rect(47f, 30f, 16f, 12f))
        seg(55f, 30f, 55f, 42f)
        val dome = shape { moveTo(94f, 40f); arcTo(101f, 40f, 7f, 8f, 180f, 180f) }
        part(cut(rect(94f, 38f, 14f, 58f), dome))
        part(dome)
    }

    private fun igloo() = kid("igloo", "İglo", 110f, 80f, listOf("iglo", "kar", "kış", "buz", "igloo", "snow")) {
        val dome = shape { moveTo(14f, 70f); arcTo(55f, 70f, 41f, 52f, 180f, 180f) }
        val tunnel = arch(40f, 70f, 40f, 70f)
        val snow = rrect(4f, 66f, 102f, 12f, 6f)
        val d = cut(dome, tunnel, snow)
        part(d)
        for (y in floatArrayOf(34f, 50f)) strokeInside(listOf(P(8f, y), P(102f, y)), d)
        for (x in floatArrayOf(40f, 70f)) strokeInside(listOf(P(x, 18f), P(x, 34f)), d)
        for (x in floatArrayOf(26f, 55f, 84f)) strokeInside(listOf(P(x, 34f), P(x, 50f)), d)
        val tn = cut(tunnel, snow)
        part(tn)
        part(cut(arch(47f, 63f, 52f, 70f), snow))
        part(snow)
        part(star(95f, 13f, 6.5f, 3f))
    }

    private fun circusTent() = kid("circus_tent", "Sirk çadırı", 100f, 100f, listOf("sirk", "çadır", "bayrak", "gösteri", "circus", "tent")) {
        val roof = shape {
            moveTo(50f, 14f); lineTo(90f, 46f); quadTo(80f, 52f, 70f, 46f); quadTo(60f, 52f, 50f, 46f); quadTo(40f, 52f, 30f, 46f)
            quadTo(20f, 52f, 10f, 46f)
        }
        val body = rect(16f, 44f, 68f, 50f)
        val flap = poly(38f, 94f, 50f, 60f, 62f, 94f)
        val b = cut(body, roof, flap)
        part(b)
        for (x in floatArrayOf(27f, 38.5f, 61.5f, 73f)) strokeInside(listOf(P(x, 40f), P(x, 96f)), b)
        part(roof)
        strokeInside(listOf(P(50f, 14f), P(30f, 52f)), roof)
        strokeInside(listOf(P(50f, 14f), P(70f, 52f)), roof)
        part(flap)
        seg(50f, 14f, 50f, 3f)
        part(poly(50.5f, 3f, 62f, 6.5f, 50.5f, 10f))
    }

    private fun lighthouse() = kid("lighthouse", "Deniz feneri", 80f, 110f, listOf("deniz feneri", "fener", "ada", "deniz", "lighthouse", "sea")) {
        val tower = rpoly(1f, 30f, 36f, 50f, 36f, 56f, 86f, 24f, 86f)
        val lamp = rect(28f, 22f, 24f, 16f)
        val dome = shape { moveTo(26f, 24f); arcTo(40f, 24f, 14f, 12f, 180f, 180f) }
        val balcony = rrect(22f, 34f, 36f, 5f, 2f)
        val rock = blob(6f, 92f, 16f, 80f, 40f, 78f, 64f, 80f, 74f, 92f, 60f, 100f, 20f, 100f)
        val waves = shape {
            moveTo(2f, 108f); lineTo(2f, 98f); quadTo(12f, 92f, 22f, 98f); quadTo(32f, 104f, 42f, 98f); quadTo(52f, 92f, 62f, 98f)
            quadTo(72f, 104f, 78f, 98f); lineTo(78f, 108f)
        }
        part(cut(rock, tower, waves))
        val tw = cut(tower, balcony, rock)
        part(tw)
        for (y in floatArrayOf(52f, 68f)) strokeInside(listOf(P(20f, y), P(60f, y)), tw)
        part(rrect(37f, 56f, 6f, 9f, 2f))
        part(cut(lamp, dome, balcony))
        part(dome)
        part(balcony)
        part(waves)
        line { moveTo(22f, 28f); lineTo(6f, 24f) }
        line { moveTo(58f, 28f); lineTo(74f, 24f) }
    }

    private fun jungleHut() = kid("jungle_hut", "Orman kulübesi", 100f, 100f, listOf("kulübe", "orman", "hasır", "macera", "jungle hut", "hut")) {
        val roof = poly(50f, 8f, 92f, 44f, 84f, 48f, 76f, 43f, 68f, 48f, 60f, 43f, 52f, 48f, 44f, 43f, 36f, 48f, 28f, 43f, 20f, 48f, 8f, 44f)
        val hut = rect(20f, 40f, 60f, 30f)
        val floor = rrect(12f, 68f, 76f, 6f, 2f)
        listOf(rect(20f, 72f, 6f, 24f), rect(47f, 72f, 6f, 24f), rect(78f, 72f, 6f, 24f)).forEach { part(cut(it, floor)) }
        part(cut(hut, roof, floor))
        part(rect(42f, 52f, 16f, 15f))
        part(rect(25f, 52f, 10f, 9f)); part(rect(65f, 52f, 10f, 9f))
        part(roof)
        strokeInside(listOf(P(50f, 8f), P(36f, 44f)), roof)
        strokeInside(listOf(P(50f, 8f), P(64f, 44f)), roof)
        part(floor)
        seg(60f, 74f, 63f, 97f); seg(68f, 74f, 71f, 97f)
        for (k in 0..2) { val y = 80f + k * 6f; seg(60.8f + k * 0.8f, y, 68.8f + k * 0.8f, y) }
    }

    private fun dragonCave() = kid("dragon_cave", "Ejderha mağarası", 120f, 90f, listOf("mağara", "ejderha", "dağ", "hazine", "cave", "dragon")) {
        val mountain = rpoly(3f, 4f, 86f, 30f, 30f, 46f, 40f, 64f, 10f, 86f, 36f, 98f, 28f, 116f, 86f)
        val cave = shape { moveTo(38f, 86f); lineTo(38f, 66f); cubicTo(38f, 44f, 82f, 44f, 82f, 66f); lineTo(82f, 86f) }
        val snow = clip(poly(40f, 0f, 90f, 0f, 90f, 24f, 80f, 30f, 72f, 24f, 64f, 30f, 56f, 24f, 48f, 30f, 40f, 26f), mountain)
        val rockL = oval(22f, 82f, 9f, 5f)
        val rockR = oval(98f, 82f, 8f, 5f)
        part(cut(mountain, cave, snow, rockL, rockR))
        part(snow)
        part(cave)
        part(oval(52f, 64f, 5f, 3.5f)); part(oval(68f, 64f, 5f, 3.5f))
        dot(52f, 64f, 1.6f); dot(68f, 64f, 1.6f)
        part(rpoly(0.5f, 44f, 82f, 48f, 76f, 52f, 82f, 48f, 86f))
        part(rpoly(0.5f, 70f, 82f, 74f, 77f, 78f, 82f, 74f, 86f))
        part(rockL); part(rockR)
    }

    private fun spaceStation() = kid("space_station", "Uzay istasyonu", 120f, 80f, listOf("uzay istasyonu", "uzay", "güneş paneli", "uydu", "space station", "space")) {
        val hub = circle(60f, 40f, 15f)
        val truss = rect(30f, 37f, 60f, 6f)
        val panelL = rect(6f, 22f, 24f, 36f)
        val panelR = rect(90f, 22f, 24f, 36f)
        parts(cutAll(truss, hub))
        for (p in listOf(panelL, panelR)) {
            part(p)
            val x0 = p.minOf { it.x }
            for (k in 1..2) strokeInside(listOf(P(x0 + 8f * k, 20f), P(x0 + 8f * k, 60f)), p)
            for (y in floatArrayOf(34f, 46f)) strokeInside(listOf(P(x0 - 2f, y), P(x0 + 26f, y)), p)
        }
        part(cut(rrect(53f, 6f, 14f, 22f, 4f), hub)); part(cut(rrect(53f, 52f, 14f, 22f, 4f), hub))
        part(hub)
        part(circle(60f, 40f, 7f))
        seg(60f, 6f, 60f, 1.5f)
    }
}

// =====================================================================================================================
// Sihirli nesneler
// =====================================================================================================================

internal object KidMagic {

    val shapes: List<LibShape> by lazy {
        listOf(wand(), crown(), chest(), shieldSword(), carpet(), telescope(), potion(), lamp(), map(), spellBook(), compass())
    }

    private fun wand() = kid("magic_wand", "Sihirli değnek", 100f, 100f, listOf("sihirli değnek", "değnek", "yıldız", "sihir", "magic wand", "wand")) {
        val st = star(66f, 32f, 22f, 10f, 5, 0f, 1.5f)
        part(cut(capsule(16f, 90f, 54f, 46f, 4f), st))
        part(st)
        part(star(20f, 24f, 8f, 3.2f)); part(star(88f, 74f, 7f, 2.8f)); part(star(40f, 10f, 5f, 2.2f))
        part(circle(84f, 9f, 3.5f))
    }

    private fun crown() = kid("crown", "Taç", 100f, 80f, listOf("taç", "kral", "kraliçe", "masal", "crown", "king")) {
        val balls = listOf(circle(8f, 15f, 4f), circle(50f, 7f, 4f), circle(92f, 15f, 4f))
        val band = rrect(10f, 52f, 80f, 20f, 4f)
        val top = rpoly(1.5f, 12f, 56f, 8f, 18f, 30f, 40f, 50f, 10f, 70f, 40f, 92f, 18f, 88f, 56f)
        part(cut(top, band, *balls.toTypedArray()))
        parts(balls)
        part(circle(50f, 32f, 5f))
        part(band)
        part(oval(30f, 62f, 6f, 5f)); part(rpoly(1f, 50f, 55f, 57f, 62f, 50f, 69f, 43f, 62f)); part(oval(70f, 62f, 6f, 5f))
    }

    private fun chest() = kid("treasure_chest", "Hazine sandığı", 110f, 90f, listOf("hazine sandığı", "sandık", "hazine", "altın", "korsan", "treasure chest", "treasure")) {
        val lid = shape { moveTo(14f, 42f); lineTo(14f, 34f); cubicTo(14f, 14f, 96f, 14f, 96f, 34f); lineTo(96f, 42f) }
        val box = rect(14f, 42f, 82f, 42f)
        val lock = rrect(48f, 36f, 14f, 16f, 3f)
        val l = cut(lid, lock)
        val b = cut(box, lock)
        part(l)
        for (x in floatArrayOf(26f, 76f)) part(clip(rect(x, 10f, 8f, 34f), l))
        part(b)
        for (x in floatArrayOf(26f, 76f)) part(clip(rect(x, 40f, 8f, 46f), b))
        part(lock)
        ink(circle(55f, 43f, 2f)); ink(rect(54.2f, 43f, 1.6f, 5f))
        part(circle(103f, 83f, 5f)); part(circle(7f, 83f, 5f))
    }

    private fun shieldSword() = kid("shield_sword", "Kalkan ve kılıç", 100f, 110f, listOf("kalkan", "kılıç", "şövalye", "oyuncak", "shield", "sword")) {
        part(shape { moveTo(6f, 26f); quadTo(33f, 16f, 60f, 26f); lineTo(60f, 54f); quadTo(60f, 84f, 33f, 98f); quadTo(6f, 84f, 6f, 54f) })
        part(shape { moveTo(13f, 32f); quadTo(33f, 24f, 53f, 32f); lineTo(53f, 54f); quadTo(53f, 78f, 33f, 90f); quadTo(13f, 78f, 13f, 54f) })
        part(star(33f, 56f, 14f, 6f))
        val guard = rrect(68f, 72f, 24f, 6f, 3f)
        val pommel = circle(80f, 100f, 5f)
        part(cut(rpoly(0.8f, 76f, 12f, 80f, 2f, 84f, 12f, 84f, 74f, 76f, 74f), guard))
        part(guard)
        part(cut(rect(76f, 78f, 8f, 18f), guard, pommel))
        part(pommel)
    }

    private fun carpet() = kid("flying_carpet", "Uçan halı", 120f, 70f, listOf("uçan halı", "halı", "masal", "sihir", "flying carpet", "carpet")) {
        part(shape { moveTo(14f, 26f); cubicTo(40f, 16f, 70f, 36f, 106f, 22f); lineTo(110f, 44f); cubicTo(76f, 58f, 46f, 38f, 10f, 50f) })
        part(shape { moveTo(22f, 30f); cubicTo(44f, 22f, 70f, 40f, 98f, 29f); lineTo(101f, 42f); cubicTo(74f, 52f, 46f, 34f, 18f, 45f) })
        for ((x, y) in listOf(39f to 34.6f, 58f to 36.9f, 78f to 38.9f)) part(rpoly(0.5f, x - 5f, y, x, y - 3.5f, x + 5f, y, x, y + 3.5f))
        seg(14f, 26f, 7f, 21f); seg(10f, 50f, 3f, 55f); seg(106f, 22f, 113f, 17f); seg(110f, 44f, 117f, 49f)
        part(star(30f, 9f, 6.5f, 3f)); part(star(96f, 60f, 6.5f, 3f))
    }

    private fun telescope() = kid("telescope", "Teleskop", 110f, 100f, listOf("teleskop", "yıldız", "gökyüzü", "uzay", "telescope", "stars")) {
        val rot = -20f
        val front = rotate(rrect(72f, 26f, 14f, 28f, 3f), rot, 50f, 40f)
        val eyepiece = rotate(rrect(16f, 36f, 14f, 8f, 2f), rot, 50f, 40f)
        val main = rotate(rrect(28f, 32f, 46f, 16f, 3f), rot, 50f, 40f)
        val mount = circle(50f, 60f, 7f)
        val legL = capsule(50f, 60f, 22f, 96f, 2.6f)
        val legR = capsule(50f, 60f, 78f, 96f, 2.6f)
        part(cut(legL, mount)); part(cut(legR, mount))
        part(cut(rect(47f, 44f, 6f, 12f), main, mount))
        part(mount)
        part(cut(main, front, eyepiece))
        part(front); part(eyepiece)
        part(star(98f, 72f, 6f, 2.6f)); part(star(14f, 70f, 5f, 2.2f))
    }

    private fun potion() = kid("potion", "İksir şişesi", 80f, 110f, listOf("iksir", "şişe", "sihir", "büyü", "potion", "bottle")) {
        val bottle = union(circle(40f, 72f, 30f), rect(31f, 26f, 18f, 24f))
        val lip = rrect(27f, 24f, 26f, 6f, 3f)
        val liquid = clip(shape {
            moveTo(4f, 68f); quadTo(14f, 62f, 24f, 68f); quadTo(34f, 74f, 44f, 68f); quadTo(54f, 62f, 64f, 68f); quadTo(72f, 72f, 78f, 68f)
            lineTo(78f, 108f); lineTo(4f, 108f)
        }, bottle)
        part(cut(rrect(29f, 12f, 22f, 16f, 4f), lip))
        part(cut(bottle, lip))
        part(lip)
        part(liquid)
        part(circle(30f, 82f, 4f)); part(circle(48f, 90f, 3.5f)); part(circle(42f, 78f, 2.8f))
        part(star(66f, 18f, 6.5f, 3f)); part(star(12f, 30f, 6f, 2.8f))
    }

    private fun lamp() = kid("magic_lamp", "Sihirli lamba", 120f, 80f, listOf("sihirli lamba", "lamba", "masal", "sihir", "magic lamp", "oil lamp")) {
        val body = shape {
            moveTo(30f, 50f); cubicTo(30f, 30f, 80f, 30f, 82f, 46f); lineTo(106f, 30f); quadTo(112f, 28f, 110f, 34f); lineTo(84f, 58f)
            cubicTo(70f, 66f, 36f, 66f, 30f, 50f)
        }
        val knob = circle(56f, 23f, 4f)
        val lid = cut(shape { moveTo(44f, 36f); quadTo(56f, 18f, 68f, 36f) }, knob)
        val base = rrect(42f, 60f, 28f, 8f, 3f)
        part(cut(tube(5f, 5f, 31f, 42f, 16f, 38f, 12f, 48f, 18f, 58f, 31f, 56f), body))
        part(cut(body, lid, knob, base))
        part(oval(56f, 49f, 6f, 4f))
        part(lid); part(knob)
        part(base)
        curve(108f, 26f, 104f, 18f, 110f, 10f, 105f, 3f)
    }

    private fun map() = kid("treasure_map", "Hazine haritası", 110f, 90f, listOf("hazine haritası", "harita", "hazine", "korsan", "macera", "treasure map", "map")) {
        val rollL = rrect(8f, 10f, 12f, 70f, 6f)
        val rollR = rrect(90f, 10f, 12f, 70f, 6f)
        part(cut(rect(16f, 14f, 78f, 62f), rollL, rollR))
        part(rollL); part(rollR)
        part(blob(30f, 40f, 44f, 26f, 66f, 30f, 80f, 44f, 72f, 62f, 50f, 66f, 34f, 58f))
        seg(62f, 42f, 70f, 50f); seg(70f, 42f, 62f, 50f)
        for (k in 0..3) { val t0 = k / 4f; val t1 = t0 + 0.13f; seg(36f + 24f * t0, 54f - 8f * t0, 36f + 24f * t1, 54f - 8f * t1) }
        part(star(83f, 69f, 6f, 2.8f, 4))
        curve(24f, 24f, 28f, 22f, 32f, 24f)
    }

    private fun spellBook() = kid("spell_book", "Büyü kitabı", 110f, 80f, listOf("büyü kitabı", "kitap", "sihir", "masal", "spell book", "book")) {
        val pageL = shape { moveTo(10f, 18f); quadTo(32f, 10f, 55f, 22f); lineTo(55f, 72f); quadTo(32f, 62f, 10f, 68f) }
        val pageR = mirror(pageL, 55f)
        part(cut(rpoly(3f, 6f, 20f, 55f, 26f, 104f, 20f, 104f, 74f, 55f, 78f, 6f, 74f), pageL, pageR))
        part(pageL); part(pageR)
        part(star(32f, 38f, 9f, 4f))
        part(cut(circle(78f, 38f, 9f), circle(82f, 35f, 7.5f)))
        seg(18f, 54f, 46f, 56f); seg(64f, 54f, 92f, 52f); seg(64f, 60f, 88f, 58f)
    }

    private fun compass() = kid("compass", "Pusula", 100f, 100f, listOf("pusula", "yön", "harita", "macera", "compass", "direction")) {
        val ring = circle(50f, 54f, 40f)
        val pin = circle(50f, 54f, 4f)
        part(cut(circle(50f, 10f, 7f), ring))
        part(ring)
        part(circle(50f, 54f, 32f))
        part(cut(rpoly(0.5f, 50f, 26f, 58f, 54f, 42f, 54f), pin))
        part(cut(rpoly(0.5f, 42f, 54f, 58f, 54f, 50f, 82f), pin))
        part(pin)
        seg(20f, 54f, 26f, 54f); seg(74f, 54f, 80f, 54f)
    }
}
