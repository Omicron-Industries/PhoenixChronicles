#version 150

uniform sampler2D Sampler0;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

float hash(float n) {
    return fract(sin(n) * 43758.5453123);
}

void main() {
    vec2 uv = texCoord;

    // Shear the quad in half: the two halves slide apart horizontally with a stepped
    // (quantized, not smooth) offset so it reads as a glitch jump rather than a slide.
    float half_ = step(0.5, uv.y);
    float glitchStep = floor(Time * 6.0);
    float shift = (hash(glitchStep + half_ * 13.0) - 0.5) * 0.16;
    uv.x += mix(-shift, shift, half_);

    // Thin RGB-channel-split band pulsing at the seam.
    float seamDist = abs(texCoord.y - 0.5);
    float seamGlitch = smoothstep(0.06, 0.0, seamDist) * (0.5 + 0.5 * sin(Time * 20.0));

    vec4 base = texture(Sampler0, clamp(uv, 0.0, 1.0));
    vec4 rShift = texture(Sampler0, clamp(uv + vec2(seamGlitch * 0.08, 0.0), 0.0, 1.0));
    vec4 bShift = texture(Sampler0, clamp(uv - vec2(seamGlitch * 0.08, 0.0), 0.0, 1.0));

    // The base texture is a plain white silhouette, so splitting its (identical) R/G/B values
    // does nothing visible except at the alpha edge - force distinct per-channel tinting instead
    // (cyan/magenta split) so the effect actually reads as color separation, not just a shape
    // wobbling slightly.
    float glitchAmt = seamGlitch + step(0.5, uv.y) * 0.15;
    vec3 tinted = mix(vec3(1.0), vec3(1.0, 0.25, 0.85) * rShift.rgb + vec3(0.0, 0.0, 0.0),
            clamp(glitchAmt * 2.0, 0.0, 1.0));
    tinted = mix(tinted, vec3(0.25, 1.0, 1.0) * bShift.rgb, clamp(glitchAmt * 2.0, 0.0, 1.0) * 0.6);

    float alpha = max(base.a, max(rShift.a, bShift.a));
    fragColor = vec4(tinted, alpha);
}
