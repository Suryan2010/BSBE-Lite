$input v_color0, v_fog, v_position, v_worldpos, v_texcoord0, v_lightmapUV
#include <bgfx_shader.sh>

uniform vec4 FogAndDistanceControl;
uniform vec4 ViewPositionAndTime;
uniform vec4 FogColor;

SAMPLER2D(s_MatTexture, 0);
SAMPLER2D(s_SeasonsTexture, 1);
SAMPLER2D(s_LightMapTexture, 2);

#include "../../common.glsl"

float cwav(vec2 pos){
	float wave = (1.0 - noise2d(pos * 1.3 - ViewPositionAndTime.w * 1.5)) + noise2d(pos + ViewPositionAndTime.w);
	return wave;
}
vec3 calcnw(vec3 n,vec2 cpos){
	float w1 = cwav(cpos);
	float w2 = cwav(vec2(cpos.x - 0.02, cpos.y));
	float w3 = cwav(vec2(cpos.x, cpos.y - 0.02));
	float dx = w1 - w2;
	float dy = w1 - w3;
	vec3 wn = normalize(vec3(dx, dy, 1.0)) * 0.5 + 0.5;
	mat3 tbn = mat3(abs(n).y + n.z, 0.0, -n.x, 0.0, -abs(n).x - abs(n).z, abs(n).y, n);
		wn = wn * 2.0 - 1.0;
		wn = normalize(mul(wn, tbn));
	return wn;
}

float fschlick(float f0, float ndv){
	return f0 + (1.0 - f0) * sqr5(1.0 - ndv);
}
vec4 reflection(vec4 diff, vec3 wpos, vec3 n, vec3 lsc, vec2 uv1, float dfog, float nfog, float rain){
	vec3 rv = reflect(normalize(wpos), n);
	vec3 vdir = normalize(-wpos);
	float ndv = max0(dot(n, vdir));
	float zen = max0(rv.y) * 1.2;
	float fresnel = fschlick(0.5, ndv) * uv1.y;
	diff = mix(diff,vec4_splat(0.0),uv1.y);
	vec3 skyc = sr(rv,FogColor.rgb,dfog,nfog,rain,FogAndDistanceControl.x);
	diff = mix(diff, vec4(skyc + (lsc * 0.5), 1.0), fresnel);
		rv = rv / rv.y;
		fresnel = fschlick(0.3, ndv) * uv1.y;
	diff = mix(diff, vec4(ccc(FogColor.rgb,dfog,nfog,rain), 1.0), cmap(rv.xz,ViewPositionAndTime.w,rain) * zen * fresnel);
	float ndh = max0(dot(n, normalize(vdir + vec3(-0.98, 0.173, 0.0))));
	diff += pow(ndh, 230.0) * vec4(skyc, 1.0) * dfog;
	diff.rgb *= max(uv1.x, smoothstep(0.9,0.94,uv1.y) * 0.7 + 0.3);
	return diff;
}

vec3 illum(vec3 diff, vec3 n, vec3 lsc, vec2 uv1, vec2 uv0, float lmb, float rain){
	float dusk = min(smoothstep(0.3, 0.5, lmb), smoothstep(1.0, 0.8, lmb)) * (1.0 - rain);
	float night = saturate(smoothstep(1.0, 0.2, lmb) * 1.5);
	float smap = mix(mix(mix(1.0, 0.2, max0(abs(n.x))), 0.0, smoothstep(0.94, 0.92, uv1.y)), 0.0, rain);
		smap = mix(smap, 1.0, smoothstep(lmb * uv1.y, 1.0, uv1.x));
	vec3 almap = texture2D(s_LightMapTexture, vec2(0.0, uv1.y)).rgb * 0.2;
		almap += float(texture2DLod(s_MatTexture, uv0, 0.0).a > 0.91 && texture2DLod(s_MatTexture, uv0, 0.0).a < 0.93) * 3.0;
		almap += lsc;
	vec3 ambc = mix(mix(vec3(1.1, 1.1, 0.8), vec3(1.0, 0.5, 0.0), dusk), vec3(0.05, 0.15, 0.4), night) * smap;
		almap += ambc;
	return diff * almap;
}

void main() {
    vec4 diffuse;

#if defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY)
    diffuse.rgb = vec3(1.0, 1.0, 1.0);
#else
    diffuse = texture2D(s_MatTexture, v_texcoord0);

#if defined(ALPHA_TEST)
    if (diffuse.a < 0.5) {
        discard;
    }
#endif

#if defined(SEASONS) && (defined(OPAQUE) || defined(ALPHA_TEST))
    diffuse.rgb *=
        mix(vec3(1.0, 1.0, 1.0),
            texture2D(s_SeasonsTexture, v_color0.xy).rgb * 2.0, v_color0.b);
    diffuse.rgb *= v_color0.aaa;
#else
	diffuse *= v_color0;
#endif
#endif

#ifndef TRANSPARENT
    diffuse.a = 1.0;
#endif

	diffuse.rgb = tl(diffuse.rgb);

	bool waterd = false;
#ifdef TRANSPARENT
	waterd = v_color0.a > 0.4 && v_color0.a < 0.6;
#endif

	vec3 n = normalize(cross(dFdx(v_position.xyz), dFdy(v_position.xyz)));
	float lmb = texture2D(s_LightMapTexture, vec2(0, 1)).r;
	float rain = smoothstep(.6,.3,FogAndDistanceControl.x);
	float nfog = pow(saturate(1.-FogColor.r*1.5),1.2);
	float dfog = saturate((FogColor.r-.15)*1.25)*(1.-FogColor.b);
	float bls = max(v_lightmapUV.x * smoothstep(lmb * v_lightmapUV.y, 1.0, v_lightmapUV.x), v_lightmapUV.x * rain * v_lightmapUV.y);
	vec3 lsc = vec3(1.0, 0.35, 0.0) * bls + sqr5(bls);
	diffuse.rgb = illum(diffuse.rgb, n, lsc, v_lightmapUV, v_texcoord0, lmb, rain);
	if(waterd){
		n = calcnw(n,v_position.xz);
		diffuse = reflection(diffuse, v_worldpos, n, lsc, v_lightmapUV, dfog, nfog, rain);
	}
	if(FogAndDistanceControl.x == 0.0){
		float caus = cwav(v_position.xz);
		if(!waterd) diffuse.rgb = vec3(0.3, 0.6, 1.0) * diffuse.rgb + diffuse.rgb * max0(caus) * v_lightmapUV.y;
		diffuse.rgb += diffuse.rgb * (v_lightmapUV.x * v_lightmapUV.x) * (1.0 - v_lightmapUV.y);
	}
	vec3 newfc = sr(normalize(v_worldpos),FogColor.rgb,dfog,nfog,rain,FogAndDistanceControl.x);
	diffuse.rgb = mix(diffuse.rgb, newfc, v_fog.a);

	vec3 curr = unchartedModified(diffuse.rgb * 5.0);
	vec3 ws = 1.0 / unchartedModified(vec3_splat(12.0));
		curr *= ws;
	diffuse.rgb = pow(curr, vec3_splat(1.0 / 2.2));
	diffuse.rgb = saturate(diffuse.rgb);

    float gray = luma(diffuse.rgb);
    diffuse.rgb = mix(vec3_splat(gray), diffuse.rgb, 1.1);

    gl_FragColor = diffuse;
}