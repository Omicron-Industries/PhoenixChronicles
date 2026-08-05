#version 150

uniform sampler2D Sampler0;
uniform float Time;
// Ratio of the drawn quad's size to the node's own icon size (same value as
// SunBackground's own "scale" constructor field, 1.0 = quad IS the icon, no extra room). texCoord
// (and therefore uv/dist below) always spans the whole DRAWN quad regardless of Scale, so the
// icon's own real edge sits at dist = 0.5 / Scale - smaller than the quad's own edge (dist = 0.5)
// whenever Scale > 1. Used to size the core so it can deliberately poke past the icon's edge
// instead of always being tiny relative to whatever room "scale" left around it.
uniform float Scale;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = texCoord - 0.5;
    float dist = length(uv);
    float angle = atan(uv.y, uv.x);

    // The shape mask (sampled below) fills almost the entire drawn quad but not quite all of it -
    // its own inscribed edge sits at roughly dist 0.44-0.48 depending on the node's shape (a
    // circle's is ~0.484; pointier shapes like diamond/star pull it in further). Fading the
    // core/rays out to a flat 0.5 (the quad's bare edge) meant the mask's own hard boundary
    // clipped them abruptly WHILE they were still fairly bright, reading as "cut off" rather than
    // a natural taper. Fading out at 0.44 instead keeps the whole effect comfortably inside every
    // shape's mask, so it always finishes fading on its own before anything clips it.
    float outerLimit = 0.44;

    // The core's radius is set relative to the icon's own edge (0.5 / Scale), not a fixed
    // fraction of the drawn quad - that's what lets it deliberately spill past the icon instead of
    // always sitting tiny and well inside it regardless of how much "scale" room is available
    // (which is what happened when this was a flat 0.16, a value only ever big enough to reach the
    // icon's edge at scale > ~3). Clamped so it never itself reaches outerLimit, leaving room for
    // the rays to still read as a distinct band beyond the core.
    float iconEdge = 0.5 / max(Scale, 0.01);
    float pulse = 0.5 + 0.5 * sin(Time * 2.0);
    float coreRadius = clamp(iconEdge * 1.1, 0.05, outerLimit - 0.05) + pulse * 0.02;
    float core = smoothstep(coreRadius, 0.0, dist);

    // Wider (lower pow exponent), longer-reaching bands that rotate over time - these read as
    // circling tendrils rather than the previous thin, alpha-starved spikes. Visible from partway
    // through the core's own radius out to outerLimit, so they occupy whatever room "scale" leaves
    // beyond the core while still fully fading out before the shape mask's own edge.
    float rays = pow(max(0.0, cos((angle + Time * 0.6) * 8.0)), 1.6);
    float rayMask = smoothstep(outerLimit, coreRadius * 0.6, dist) * rays;

    vec3 coreColor = mix(vec3(1.0, 0.55, 0.1), vec3(1.0, 0.95, 0.6), core);
    vec3 rayColor = vec3(1.0, 0.8, 0.35);
    vec3 sunColor = mix(coreColor, rayColor, rayMask * (1.0 - core));

    float alpha = clamp(core + rayMask * 0.9, 0.0, 1.0);

    // Sampler0 is the node's actual shape mask (square/circle/diamond/hexagon/... - see
    // BackgroundRenderUtil.maskTextureFor), not a texture we're drawing - clip to it so the sun
    // fills the node's real silhouette instead of always being a plain circle regardless of shape.
    float shapeMask = texture(Sampler0, texCoord).a;
    fragColor = vec4(sunColor, alpha * shapeMask);
}
