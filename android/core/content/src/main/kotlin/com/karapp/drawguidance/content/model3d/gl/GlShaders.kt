package com.karapp.drawguidance.content.model3d.gl

/**
 * v24 ekran kartı çizicisinin GLSL ES 1.00 gölgelendiricileri. Yüzey ışığı yazılım çizicisiyle ([com.karapp.drawguidance
 * .content.model3d.Renderer]) aynı modeldir: Lambert + Blinn-Phong parlama, metalde gökyüzü/zemin yansıması, camda
 * kenara doğru artan örtücülük (Fresnel), ışıklı malzeme ve seçili yüzde vurgu rengi karışımı.
 */
internal object GlShaders {

    private const val PRECISION = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
"""

    const val SURFACE_VS = """
attribute vec3 aPos;
attribute vec3 aNormal;
attribute vec2 aUv;
uniform mat4 uViewProj;
uniform mat4 uModel;
uniform mat3 uNormal;
uniform float uUvScale;
uniform vec3 uGradAxis;
uniform vec2 uGradRange;
varying vec3 vN;
varying vec2 vUv;
varying float vG;
void main() {
    vN = uNormal * aNormal;
    vUv = aUv * uUvScale;
    vG = (dot(aPos, uGradAxis) - uGradRange.x) * uGradRange.y;
    gl_Position = uViewProj * (uModel * vec4(aPos, 1.0));
}
"""

    val SURFACE_FS = PRECISION + """
varying vec3 vN;
varying vec2 vUv;
uniform vec3 uL;
uniform vec3 uH;
uniform vec3 uT;
uniform vec3 uLightColor;
uniform float uAmbient;
uniform float uIntensity;
uniform vec4 uColor;
uniform float uKind;
uniform float uSpec;
uniform float uExp;
uniform float uTextured;
uniform sampler2D uTex;
uniform float uSelected;
uniform vec3 uHighlight;
uniform vec4 uGradColor;
varying float vG;
void main() {
    vec3 n = normalize(vN);
    float ndv = dot(n, uT);
    if (ndv < 0.0) n = -n;
    vec3 base = uColor.rgb;
    if (uGradColor.a > 0.5) base = mix(uColor.rgb, uGradColor.rgb, clamp(vG, 0.0, 1.0));
    else if (uTextured > 0.5) base *= texture2D(uTex, vUv).rgb;
    if (uKind > 3.5) {
        gl_FragColor = vec4(min(base * 1.08, vec3(1.0)), uColor.a);
        return;
    }
    float ndl = dot(n, uL);
    float amb = uAmbient * (0.82 + 0.18 * n.y);
    float diff = max(0.0, ndl) * uIntensity * (1.0 - uAmbient * 0.5);
    float metal = (uKind > 1.5 && uKind < 2.5) ? 1.0 : 0.0;
    float lit = amb + diff * mix(1.0, 0.75, metal);
    vec3 col = base * lit * (0.6 + 0.4 * uLightColor);
    if (uSpec > 0.0 && ndl > 0.0) {
        float ndh = max(0.0, dot(n, uH));
        float s = uSpec * pow(ndh, uExp) * uIntensity;
        col += mix(s * uLightColor, s * (0.3 + 0.7 * base), metal);
    }
    if (metal > 0.5) {
        float rv = 2.0 * abs(ndv) * n.y - uT.y;
        float e = clamp(rv * 0.5 + 0.5, 0.0, 1.0);
        col += (0.18 + 0.55 * e * e) * base * 0.6;
    }
    float alpha = uColor.a;
    if (uKind > 2.5 && uKind < 3.5) {
        float fr = 1.0 - abs(ndv);
        fr = fr * fr;
        alpha = clamp(uColor.a * (0.3 + 0.6 * fr) + 0.08, 0.05, 1.0);
        col += vec3(0.25, 0.25, 0.3) * fr;
    }
    if (uSelected > 0.5) col = col * 0.55 + uHighlight * 0.45;
    gl_FragColor = vec4(clamp(col, 0.0, 1.0), alpha);
}
"""

    /**
     * Ekran uzayında kalınlığı olan çizgi (kenar, ızgara, 3B fırça çizgisi): her parça iki üçgen; köşeler ekranda
     * çizgiye dik yönde açılır. Dış hat adayı kenar (aInfo.z = 1) yalnız iki yüzünden biri bakana dönükken çizilir.
     * Uçlar bakana doğru [uBias] kadar çekilir (yüzeyin içinde kaybolmasın; ekrandaki yeri değişmez).
     */
    const val LINE_VS = """
attribute vec3 aP0;
attribute vec3 aP1;
attribute vec3 aN1;
attribute vec3 aN2;
attribute vec3 aInfo;
uniform mat4 uViewProj;
uniform mat4 uModel;
uniform mat3 uNormal;
uniform vec3 uEye;
uniform vec3 uToViewer;
uniform float uPersp;
uniform vec2 uHalf;
uniform float uWidth;
uniform float uWorldWidth;
uniform float uBias;
uniform float uWire;
void main() {
    vec3 w0 = (uModel * vec4(aP0, 1.0)).xyz;
    vec3 w1 = (uModel * vec4(aP1, 1.0)).xyz;
    float keep = 1.0;
    vec3 v0 = uToViewer;
    vec3 v1 = uToViewer;
    if (uPersp > 0.5) {
        v0 = normalize(uEye - w0);
        v1 = normalize(uEye - w1);
    }
    if (aInfo.z > 0.5 && uWire < 0.5) {
        vec3 v = uPersp > 0.5 ? (uEye - w0) : uToViewer;
        float a = dot(uNormal * aN1, v);
        float b = dot(uNormal * aN2, v);
        if ((a > 0.0) == (b > 0.0)) keep = 0.0;
    }
    vec4 c0 = uViewProj * vec4(w0 + v0 * uBias, 1.0);
    vec4 c1 = uViewProj * vec4(w1 + v1 * uBias, 1.0);
    if (c0.w <= 1e-5 || c1.w <= 1e-5) keep = 0.0;
    vec2 s0 = c0.xy / c0.w * uHalf;
    vec2 s1 = c1.xy / c1.w * uHalf;
    vec2 d = s1 - s0;
    float len = length(d);
    vec2 dir = len > 1e-4 ? d / len : vec2(1.0, 0.0);
    vec2 nrm = vec2(-dir.y, dir.x);
    vec4 c = aInfo.x < 0.5 ? c0 : c1;
    float hw = 0.5 * uWidth;
    if (uWorldWidth > 0.0) hw = max(0.6, 0.5 * uWorldWidth / (uPersp > 0.5 ? c.w : 1.0));
    float along = aInfo.x < 0.5 ? -1.0 : 1.0;
    vec2 off = (nrm * aInfo.y + dir * along) * hw;
    c.xy += off / uHalf * c.w;
    gl_Position = keep > 0.5 ? c : vec4(0.0, 0.0, 2.0, 1.0);
}
"""

    val LINE_FS = PRECISION + """
uniform vec4 uColor;
void main() { gl_FragColor = uColor; }
"""

    /** Gölge: parçanın köşeleri ışık yönünde zemine izdüşürülür; zemin bölgesi dokuya (beyaz = gölge). */
    const val SHADOW_VS = """
attribute vec3 aPos;
uniform mat4 uModel;
uniform vec3 uL;
uniform float uLy;
uniform float uGround;
uniform vec3 uRegion;
void main() {
    vec3 w = (uModel * vec4(aPos, 1.0)).xyz;
    float t = (w.y - uGround) / uLy;
    vec2 p = vec2(w.x - uL.x * t, w.z - uL.z * t);
    gl_Position = vec4((p - uRegion.xy) / uRegion.z, 0.0, 1.0);
}
"""

    val WHITE_FS = PRECISION + """
void main() { gl_FragColor = vec4(1.0); }
"""

    const val BLUR_VS = """
attribute vec2 aPos;
varying vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    val BLUR_FS = PRECISION + """
uniform sampler2D uTex;
uniform vec2 uStep;
varying vec2 vUv;
void main() {
    float s = 0.0;
    for (int i = -4; i <= 4; i++) s += texture2D(uTex, vUv + uStep * float(i)).r;
    gl_FragColor = vec4(s / 9.0, 0.0, 0.0, 1.0);
}
"""

    const val GROUND_VS = """
attribute vec3 aPos;
uniform mat4 uViewProj;
uniform vec3 uRegion;
varying vec2 vUv;
void main() {
    vUv = (aPos.xz - uRegion.xy) / uRegion.z * 0.5 + 0.5;
    gl_Position = uViewProj * vec4(aPos, 1.0);
}
"""

    val GROUND_FS = PRECISION + """
uniform sampler2D uTex;
uniform float uStrength;
varying vec2 vUv;
void main() {
    float m = texture2D(uTex, vUv).r;
    gl_FragColor = vec4(0.0, 0.0, 0.0, clamp(m * uStrength, 0.0, 0.6));
}
"""
}
