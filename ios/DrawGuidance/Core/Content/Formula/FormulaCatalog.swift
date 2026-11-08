import Foundation

/// Hazır formüller (Android `FormulaCatalog`, aynı kimlik/başlık/kaynak). Kısaltmalar: `@a` → `\slot{a}{a}`,
/// `@{x1|x_1}` → `\slot{x1}{x_1}`. Bu dosya Android kaynağından üretilmiştir.
enum FormulaCatalog {
    static let categories: [FormulaCategory] = [
        category("algebra", "Cebir", [
            t("algebra.power", "Üslü ifade", #"@a^{@n}"#),
            t("algebra.power_product", "Üs kuralı: çarpım", #"@a^{@m} \cdot @a^{@n} = @a^{@m + @n}"#),
            t("algebra.power_quotient", "Üs kuralı: bölüm", #"\frac{@a^{@m}}{@a^{@n}} = @a^{@m - @n}"#),
            t("algebra.power_of_power", "Üssün üssü", #"\left(@a^{@m}\right)^{@n} = @a^{@m \cdot @n}"#),
            t("algebra.negative_exponent", "Negatif üs", #"@a^{-@n} = \frac{1}{@a^{@n}}"#),
            t("algebra.sqrt", "Karekök", #"\sqrt{@a^2} = |@a|"#),
            t("algebra.sqrt_product", "Kareköklerin çarpımı", #"\sqrt{@a} \cdot \sqrt{@b} = \sqrt{@a @b}"#),
            t("algebra.nth_root", "n. dereceden kök", #"\sqrt[@n]{@a^{@m}} = @a^{\frac{@m}{@n}}"#),
            t("algebra.quadratic", "İkinci dereceden denklem", #"@a x^2 + @b x + @c = 0"#),
            t("algebra.quadratic_formula", "Kök formülü", #"x_{1,2} = \frac{-@b \pm \sqrt{@b^2 - 4@a@c}}{2@a}"#),
            t("algebra.quadratic_formula_delta", "Kök formülü (Δ ile)", #"x_{1,2} = \frac{-@b \pm \sqrt{\Delta}}{2@a}"#),
            t("algebra.discriminant", "Diskriminant", #"\Delta = @b^2 - 4@a@c"#),
            t("algebra.vieta", "Kökler toplamı ve çarpımı", #"x_1 + x_2 = -\frac{@b}{@a}, \quad x_1 \cdot x_2 = \frac{@c}{@a}"#),
            t("algebra.square_of_sum", "Toplamın karesi", #"(@a + @b)^2 = @a^2 + 2@a@b + @b^2"#),
            t("algebra.square_of_difference", "Farkın karesi", #"(@a - @b)^2 = @a^2 - 2@a@b + @b^2"#),
            t("algebra.difference_of_squares", "İki kare farkı", #"@a^2 - @b^2 = (@a - @b)(@a + @b)"#),
            t("algebra.cube_of_sum", "Toplamın küpü", #"(@a + @b)^3 = @a^3 + 3@a^2@b + 3@a@b^2 + @b^3"#),
            t("algebra.absolute_value", "Mutlak değer", #"|@x| = \begin{cases} @x, & @x \ge 0 \\ -@x, & @x < 0 \end{cases}"#),
            t("algebra.absolute_inequality", "Mutlak değerli eşitsizlik", #"|x - @a| < @r \iff @a - @r < x < @a + @r"#),
            t("algebra.linear_inequality", "Birinci dereceden eşitsizlik", #"@a x + @b > @c"#),
            t("algebra.double_inequality", "Çift taraflı eşitsizlik", #"@a \le x \le @b"#),
            t("algebra.linear_system", "İki bilinmeyenli denklem sistemi", #"\begin{cases} @{a1|a_1} x + @{b1|b_1} y = @{c1|c_1} \\ @{a2|a_2} x + @{b2|b_2} y = @{c2|c_2} \end{cases}"#),
            t("algebra.log_definition", "Logaritma tanımı", #"\log_{@a} @b = @c \iff @a^{@c} = @b"#),
            t("algebra.log_product", "Logaritma: çarpım kuralı", #"\log_{@a}(@x \cdot @y) = \log_{@a} @x + \log_{@a} @y"#),
            t("algebra.log_quotient", "Logaritma: bölüm kuralı", #"\log_{@a}\left(\frac{@x}{@y}\right) = \log_{@a} @x - \log_{@a} @y"#),
            t("algebra.log_power", "Logaritma: üs kuralı", #"\log_{@a} @x^{@n} = @n \cdot \log_{@a} @x"#),
            t("algebra.log_change_base", "Taban değiştirme", #"\log_{@a} @b = \frac{\log_{@c} @b}{\log_{@c} @a}"#),
            t("algebra.ratio", "Oran-orantı", #"\frac{@a}{@b} = \frac{@c}{@d} \Rightarrow @a \cdot @d = @b \cdot @c"#),
            t("algebra.inverse_ratio", "Ters orantı", #"@{x1|x_1} \cdot @{y1|y_1} = @{x2|x_2} \cdot @{y2|y_2}"#),
        ]),
        category("functions", "Fonksiyonlar", [
            t("functions.linear", "Doğrusal fonksiyon", #"f(x) = @a x + @b"#),
            t("functions.quadratic", "İkinci dereceden fonksiyon", #"f(x) = @a x^2 + @b x + @c"#),
            t("functions.vertex", "Parabolün tepe noktası", #"T(r, k), \quad r = -\frac{@b}{2@a}, \quad k = f(r)"#),
            t("functions.cubic", "Üçüncü dereceden fonksiyon", #"f(x) = @a x^3 + @b x^2 + @c x + @d"#),
            t("functions.composition", "Bileşke fonksiyon", #"(@f \circ @g)(x) = @f(@g(x))"#),
            t("functions.inverse", "Ters fonksiyon", #"f(x) = @a x + @b \Rightarrow f^{-1}(x) = \frac{x - @b}{@a}"#),
            t("functions.piecewise", "Parçalı fonksiyon", #"f(x) = \begin{cases} @{f1|x^2}, & x < @{c|0} \\ @{f2|2x + 1}, & x \ge @{c|0} \end{cases}"#),
            t("functions.exponential", "Üstel fonksiyon", #"f(x) = @a^{x}, \quad @a > 0,\ @a \ne 1"#),
            t("functions.logarithmic", "Logaritmik fonksiyon", #"f(x) = \log_{@a} x, \quad x > 0"#),
            t("functions.polynomial", "Polinom", #"P(x) = @{an|a_n} x^n + @{an1|a_{n-1}} x^{n-1} + \cdots + @{a1|a_1} x + @{a0|a_0}"#),
            t("functions.rational", "Rasyonel fonksiyon", #"f(x) = \frac{@{p|P(x)}}{@{q|Q(x)}}, \quad @{q|Q(x)} \ne 0"#),
            t("functions.absolute", "Mutlak değer fonksiyonu", #"f(x) = @a|x - @h| + @k"#),
        ]),
        category("trig", "Trigonometri", [
            t("trig.pythagorean", "Temel özdeşlik", #"\sin^2 @x + \cos^2 @x = 1"#),
            t("trig.tangent", "Tanjant", #"\tan @x = \frac{\sin @x}{\cos @x}"#),
            t("trig.cotangent", "Kotanjant", #"\cot @x = \frac{\cos @x}{\sin @x} = \frac{1}{\tan @x}"#),
            t("trig.sin_sum", "Toplam formülü (sinüs)", #"\sin(@{a|\alpha} + @{b|\beta}) = \sin @{a|\alpha} \cos @{b|\beta} + \cos @{a|\alpha} \sin @{b|\beta}"#),
            t("trig.cos_sum", "Toplam formülü (kosinüs)", #"\cos(@{a|\alpha} + @{b|\beta}) = \cos @{a|\alpha} \cos @{b|\beta} - \sin @{a|\alpha} \sin @{b|\beta}"#),
            t("trig.tan_sum", "Toplam formülü (tanjant)", #"\tan(@{a|\alpha} + @{b|\beta}) = \frac{\tan @{a|\alpha} + \tan @{b|\beta}}{1 - \tan @{a|\alpha} \tan @{b|\beta}}"#),
            t("trig.sin_double", "İki kat açı (sinüs)", #"\sin 2@x = 2\sin @x \cos @x"#),
            t("trig.cos_double", "İki kat açı (kosinüs)", #"\cos 2@x = \cos^2 @x - \sin^2 @x = 1 - 2\sin^2 @x"#),
            t("trig.law_of_sines", "Sinüs teoremi", #"\frac{@a}{\sin @A} = \frac{@b}{\sin @B} = \frac{@c}{\sin @C} = 2@R"#),
            t("trig.law_of_cosines", "Kosinüs teoremi", #"@a^2 = @b^2 + @c^2 - 2@b@c \cos @A"#),
            t("trig.rad_deg", "Radyan–derece dönüşümü", #"\frac{@D}{180^\circ} = \frac{@R}{\pi}"#),
            t("trig.triangle_area", "Üçgen alanı (sinüslü)", #"A = \frac{1}{2} @a @b \sin @C"#),
        ]),
        category("matrix", "Matris ve vektör", [
            t("matrix.m2", "2×2 matris", #"A = \begin{pmatrix} @a & @b \\ @c & @d \end{pmatrix}"#),
            t("matrix.m3", "3×3 matris", #"A = \begin{pmatrix} @{a11|a_{11}} & @{a12|a_{12}} & @{a13|a_{13}} \\ @{a21|a_{21}} & @{a22|a_{22}} & @{a23|a_{23}} \\ @{a31|a_{31}} & @{a32|a_{32}} & @{a33|a_{33}} \end{pmatrix}"#),
            t("matrix.det2", "2×2 determinant", #"\begin{vmatrix} @a & @b \\ @c & @d \end{vmatrix} = @a@d - @b@c"#),
            t("matrix.det3", "3×3 determinant", #"\begin{vmatrix} @a & @b & @c \\ @d & @e & @f \\ @g & @h & @i \end{vmatrix} = @a(@e@i - @f@h) - @b(@d@i - @f@g) + @c(@d@h - @e@g)"#),
            t("matrix.product", "Matris çarpımı", #"\begin{pmatrix} @a & @b \\ @c & @d \end{pmatrix} \begin{pmatrix} @e & @f \\ @g & @h \end{pmatrix} = \begin{pmatrix} @a@e + @b@g & @a@f + @b@h \\ @c@e + @d@g & @c@f + @d@h \end{pmatrix}"#),
            t("matrix.identity", "Birim matris", #"I_3 = \begin{pmatrix} 1 & 0 & 0 \\ 0 & 1 & 0 \\ 0 & 0 & 1 \end{pmatrix}, \quad @A \cdot I = I \cdot @A = @A"#),
            t("matrix.inverse", "Ters matris (2×2)", #"A^{-1} = \frac{1}{@a@d - @b@c} \begin{pmatrix} @d & -@b \\ -@c & @a \end{pmatrix}"#),
            t("matrix.transpose", "Devrik matris", #"\begin{pmatrix} @a & @b \\ @c & @d \end{pmatrix}^{T} = \begin{pmatrix} @a & @c \\ @b & @d \end{pmatrix}"#),
            t("matrix.vector", "Vektör", #"\vec{@v} = (@a, @b)"#),
            t("matrix.vector_magnitude", "Vektörün uzunluğu", #"|\vec{@v}| = \sqrt{@a^2 + @b^2}"#),
            t("matrix.vector_magnitude3", "Vektörün uzunluğu (3B)", #"\vec{@v} = \begin{pmatrix} @x \\ @y \\ @z \end{pmatrix}, \quad |\vec{@v}| = \sqrt{@x^2 + @y^2 + @z^2}"#),
            t("matrix.dot_product", "İç çarpım", #"\vec{u} \cdot \vec{v} = @{u1|u_1} @{v1|v_1} + @{u2|u_2} @{v2|v_2}"#),
            t("matrix.dot_angle", "İç çarpım ve açı", #"\vec{u} \cdot \vec{v} = |\vec{u}|\,|\vec{v}| \cos @{t|\theta}"#),
            t("matrix.vector_sum", "Vektör toplamı", #"\vec{u} + \vec{v} = (@{u1|u_1} + @{v1|v_1},\ @{u2|u_2} + @{v2|v_2})"#),
            t("matrix.vector_equality", "Vektör eşitliği", #"(@a, @b) = (@c, @d) \iff @a = @c \text{ ve } @b = @d"#),
            t("matrix.scalar_multiple", "Vektörün skalerle çarpımı", #"@k \cdot (@a, @b) = (@k @a, @k @b)"#),
            t("matrix.linear_system", "Doğrusal denklem sistemi (Ax = b)", #"\begin{pmatrix} @{a11|a_{11}} & @{a12|a_{12}} \\ @{a21|a_{21}} & @{a22|a_{22}} \end{pmatrix} \begin{pmatrix} x \\ y \end{pmatrix} = \begin{pmatrix} @{b1|b_1} \\ @{b2|b_2} \end{pmatrix}"#),
        ]),
        category("calculus", "Türev ve integral", [
            t("calculus.limit", "Limit", #"\lim_{x \to @a} @{f|f(x)} = @L"#),
            t("calculus.derivative_definition", "Türev tanımı", #"@f'(x) = \lim_{h \to 0} \frac{@f(x + h) - @f(x)}{h}"#),
            t("calculus.power_rule", "Kuvvet kuralı", #"\frac{d}{dx}\left(x^{@n}\right) = @n x^{@n - 1}"#),
            t("calculus.product_rule", "Çarpım kuralı", #"(@f \cdot @g)' = @f' \cdot @g + @f \cdot @g'"#),
            t("calculus.quotient_rule", "Bölüm kuralı", #"\left(\frac{@f}{@g}\right)' = \frac{@f' \cdot @g - @f \cdot @g'}{@g^2}"#),
            t("calculus.chain_rule", "Zincir kuralı", #"(@f \circ @g)'(x) = @f'(@g(x)) \cdot @g'(x)"#),
            t("calculus.indefinite_integral", "Belirsiz integral", #"\int x^{@n}\,dx = \frac{x^{@n + 1}}{@n + 1} + C, \quad @n \ne -1"#),
            t("calculus.integral_1_over_x", "1/x integrali", #"\int \frac{1}{@x}\,d@x = \ln|@x| + C"#),
            t("calculus.definite_integral", "Belirli integral", #"\int_{@a}^{@b} @{f|f(x)}\,dx = F(@b) - F(@a)"#),
            t("calculus.integration_by_parts", "Kısmi integrasyon", #"\int @u\,d@v = @u@v - \int @v\,d@u"#),
            t("calculus.area", "Eğri altındaki alan", #"A = \int_{@a}^{@b} |@{f|f(x)}|\,dx"#),
            t("calculus.area_between", "İki eğri arasındaki alan", #"A = \int_{@a}^{@b} \left(@{f|f(x)} - @{g|g(x)}\right)\,dx"#),
            t("calculus.fundamental_theorem", "Analizin temel teoremi", #"\frac{d}{dx}\int_{@a}^{x} @f(t)\,dt = @f(x)"#),
            t("calculus.ode_first_order", "Birinci mertebe diferansiyel denklem", #"y' + @{p|p(x)}\,y = @{q|q(x)}"#),
            t("calculus.ode_second_order", "İkinci mertebe diferansiyel denklem", #"@a y'' + @b y' + @c y = 0"#),
            t("calculus.partial_derivative", "Kısmi türev", #"\frac{\partial @f}{\partial @x}"#),
            t("calculus.series", "Seri toplamı", #"\sum_{@k=@{m|1}}^{@n} @{t|a_k}"#),
            t("calculus.arithmetic_series", "İlk n doğal sayının toplamı", #"\sum_{k=1}^{@n} k = \frac{@n(@n + 1)}{2}"#),
            t("calculus.geometric_series", "Geometrik seri", #"\sum_{k=0}^{\infty} @a @r^k = \frac{@a}{1 - @r}, \quad |@r| < 1"#),
            t("calculus.taylor", "Taylor serisi", #"@f(x) = \sum_{n=0}^{\infty} \frac{@f^{(n)}(@a)}{n!}(x - @a)^n"#),
            t("calculus.e_limit", "e sayısı (limit tanımı)", #"e = \lim_{@n \to \infty} \left(1 + \frac{1}{@n}\right)^{@n}"#),
        ]),
        category("probability", "Olasılık ve istatistik", [
            t("probability.factorial", "Faktöriyel", #"@n! = @n \cdot (@n - 1) \cdot (@n - 2) \cdots 2 \cdot 1"#),
            t("probability.permutation", "Permütasyon", #"P(@n, @r) = \frac{@n!}{(@n - @r)!}"#),
            t("probability.combination", "Kombinasyon", #"C(@n, @r) = \binom{@n}{@r} = \frac{@n!}{@r!\,(@n - @r)!}"#),
            t("probability.binomial_theorem", "Binom açılımı", #"(@a + @b)^{@n} = \sum_{k=0}^{@n} \binom{@n}{k} @a^{@n - k} @b^{k}"#),
            t("probability.probability", "Olasılık", #"P(@A) = \frac{s(@A)}{s(@E)}"#),
            t("probability.complement", "Tümleyen olay", #"P(@A') = 1 - P(@A)"#),
            t("probability.conditional", "Koşullu olasılık", #"P(@A \mid @B) = \frac{P(@A \cap @B)}{P(@B)}"#),
            t("probability.union", "Birleşim olasılığı", #"P(@A \cup @B) = P(@A) + P(@B) - P(@A \cap @B)"#),
            t("probability.independent", "Bağımsız olaylar", #"P(@A \cap @B) = P(@A) \cdot P(@B)"#),
            t("probability.mean", "Aritmetik ortalama", #"\bar{x} = \frac{1}{@n} \sum_{i=1}^{@n} x_i"#),
            t("probability.std_deviation", "Standart sapma", #"s = \sqrt{\frac{\sum_{i=1}^{@n} (x_i - \bar{x})^2}{@n - 1}}"#),
            t("probability.variance", "Varyans", #"\sigma^2 = \frac{1}{@n} \sum_{i=1}^{@n} (x_i - @{mu|\mu})^2"#),
            t("probability.normal", "Normal dağılım", #"f(x) = \frac{1}{@{s|\sigma}\sqrt{2\pi}}\, e^{-\frac{(x - @{mu|\mu})^2}{2@{s|\sigma}^2}}"#),
            t("probability.z_score", "Standart puan (z)", #"z = \frac{@x - @{mu|\mu}}{@{s|\sigma}}"#),
            t("probability.binomial_distribution", "Binom dağılımı", #"P(X = @k) = \binom{@n}{@k} @p^{@k} (1 - @p)^{@n - @k}"#),
            t("probability.expected_value", "Beklenen değer", #"E(X) = \sum_{i=1}^{@n} x_i \, p_i"#),
        ]),
        category("physics", "Fizik", [
            t("physics.newton2", "Newton'un 2. yasası", #"@F = @m \cdot @a"#),
            t("physics.velocity", "Hız (sabit ivme)", #"@v = @{v0|v_0} + @a @t"#),
            t("physics.position", "Yol (sabit ivme)", #"@x = @{v0|v_0} @t + \frac{1}{2} @a @t^2"#),
            t("physics.timeless", "Zamansız hız denklemi", #"@v^2 = @{v0|v_0}^2 + 2 @a \Delta @x"#),
            t("physics.average_speed", "Ortalama sürat", #"@v = \frac{\Delta @x}{\Delta @t}"#),
            t("physics.mass_energy", "Kütle-enerji eşdeğerliği", #"@E = @m @c^2"#),
            t("physics.kinetic_energy", "Kinetik enerji", #"E_k = \frac{1}{2} @m @v^2"#),
            t("physics.potential_energy", "Potansiyel enerji", #"E_p = @m @g @h"#),
            t("physics.ohm", "Ohm yasası", #"@V = @I \cdot @R"#),
            t("physics.electric_power", "Elektriksel güç", #"@P = @V \cdot @I"#),
            t("physics.momentum", "Momentum", #"\vec{p} = @m \vec{@v}"#),
            t("physics.work", "İş", #"@W = @F \cdot @d"#),
            t("physics.work_angle", "İş (açılı kuvvet)", #"@W = @F \cdot @d \cdot \cos @{t|\theta}"#),
            t("physics.power", "Güç", #"@P = \frac{@W}{@t}"#),
            t("physics.gravitation", "Newton'un evrensel çekim yasası", #"@F = @G \frac{@{m1|m_1} @{m2|m_2}}{@r^2}"#),
            t("physics.ideal_gas", "İdeal gaz yasası", #"@P @V = @n @R @T"#),
            t("physics.wave", "Dalga denklemi", #"@v = @{l|\lambda} \cdot @f"#),
            t("physics.coulomb", "Coulomb yasası", #"@F = @k \frac{@{q1|q_1} @{q2|q_2}}{@r^2}"#),
            t("physics.density", "Özkütle", #"@d = \frac{@m}{@V}"#),
            t("physics.pressure", "Basınç", #"@P = \frac{@F}{@A}"#),
            t("physics.hooke", "Hooke yasası", #"@F = -@k @x"#),
            t("physics.pendulum", "Basit sarkacın periyodu", #"@T = 2\pi \sqrt{\frac{@L}{@g}}"#),
            t("physics.snell", "Snell yasası", #"@{n1|n_1} \sin @{t1|\theta_1} = @{n2|n_2} \sin @{t2|\theta_2}"#),
        ]),
        category("geometry", "Geometri", [
            t("geometry.pythagoras", "Pisagor teoremi", #"@a^2 + @b^2 = @c^2"#),
            t("geometry.circle_area", "Daire alanı", #"A = \pi @r^2"#),
            t("geometry.circumference", "Çemberin çevresi", #"Ç = 2\pi @r"#),
            t("geometry.triangle_area", "Üçgen alanı", #"A = \frac{@a \cdot @h}{2}"#),
            t("geometry.rectangle_area", "Dikdörtgen alanı", #"A = @a \cdot @b"#),
            t("geometry.trapezoid_area", "Yamuk alanı", #"A = \frac{(@a + @c) \cdot @h}{2}"#),
            t("geometry.heron", "Heron formülü", #"A = \sqrt{u(u - @a)(u - @b)(u - @c)}, \quad u = \frac{@a + @b + @c}{2}"#),
            t("geometry.sphere_volume", "Küre hacmi", #"V = \frac{4}{3} \pi @r^3"#),
            t("geometry.sphere_area", "Küre yüzey alanı", #"A = 4\pi @r^2"#),
            t("geometry.cylinder_volume", "Silindir hacmi", #"V = \pi @r^2 @h"#),
            t("geometry.cone_volume", "Koni hacmi", #"V = \frac{1}{3} \pi @r^2 @h"#),
            t("geometry.cube_volume", "Küp hacmi", #"V = @a^3"#),
            t("geometry.distance", "İki nokta arası uzaklık", #"|AB| = \sqrt{(@{x2|x_2} - @{x1|x_1})^2 + (@{y2|y_2} - @{y1|y_1})^2}"#),
            t("geometry.line", "Doğru denklemi", #"y - @{y1|y_1} = @m(x - @{x1|x_1})"#),
            t("geometry.slope", "Eğim", #"@m = \frac{@{y2|y_2} - @{y1|y_1}}{@{x2|x_2} - @{x1|x_1}}"#),
            t("geometry.circle_equation", "Çember denklemi", #"(x - @a)^2 + (y - @b)^2 = @r^2"#),
            t("geometry.midpoint", "Orta nokta", #"M = \left(\frac{@{x1|x_1} + @{x2|x_2}}{2}, \frac{@{y1|y_1} + @{y2|y_2}}{2}\right)"#),
            t("geometry.interior_angles", "Çokgenin iç açıları toplamı", #"(@n - 2) \cdot 180^\circ"#),
        ]),
    ]

    static func find(_ id: String) -> FormulaTemplate? {
        for c in categories { if let t = c.templates.first(where: { $0.id == id }) { return t } }
        return nil
    }

    private static func category(_ id: String, _ title: String, _ templates: [FormulaTemplate]) -> FormulaCategory {
        FormulaCategory(id: id, title: title, templates: templates)
    }

    private static func t(_ id: String, _ title: String, _ shorthand: String) -> FormulaTemplate {
        FormulaTemplate(id: id, title: title, source: expand(shorthand))
    }

    /// `@a` → `\slot{a}{a}`, `@{ad}` → `\slot{ad}{ad}`, `@{ad|varsayılan}` → `\slot{ad}{varsayılan}`.
    static func expand(_ text: String) -> String {
        let src = Array(text)
        var sb = ""
        var i = 0
        while i < src.count {
            let c = src[i]
            if c == "@" && i + 1 < src.count {
                if src[i + 1] == "{" {
                    let e0 = src.index(of: "}", from: i + 2)
                    var close = e0 < 0 ? src.count : e0
                    var d = 0, k = i + 1
                    while k < src.count {
                        if src[k] == "{" { d += 1 }
                        if src[k] == "}" { d -= 1; if d == 0 { close = k; break } }
                        k += 1
                    }
                    let inner = Array(src[(i + 2)..<close])
                    let bar = inner.index(of: "|", from: 0)
                    let name = bar < 0 ? String(inner) : inner.str(0, bar)
                    let def = bar < 0 ? String(inner) : inner.str(bar + 1, inner.count)
                    sb += "\\slot{\(name)}{\(def)}"
                    i = close + 1
                    continue
                }
                if src[i + 1].isLetter {
                    let n = src[i + 1]
                    sb += "\\slot{\(n)}{\(n)}"
                    i += 2
                    continue
                }
            }
            sb.append(c)
            i += 1
        }
        return sb
    }
}
