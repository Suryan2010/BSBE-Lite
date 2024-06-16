$input v_position, v_fogColor, v_fogDistControl
#include <bgfx_shader.sh>
#include "../../common.glsl"

void main() {
    vec3 ajp = normalize(vec3(v_position.x,-v_position.y,-v_position.z));
	float rain = smoothstep(.6,.3,v_fogDistControl.x);
	float nfog = pow(saturate(1.-v_fogColor.r*1.5),1.2);
	float dfog = saturate((v_fogColor.r-.15)*1.25)*(1.-v_fogColor.b);

    vec3 dpos = ajp / ajp.y;
	float zen = max0(ajp.y);
    float cm = cmap(dpos.xz,v_position.w,rain);

	vec4 color = vec4(sr(ajp,v_fogColor.rgb,dfog,nfog,rain,v_fogDistControl.x), exp(-saturate(ajp.y) * 5.0));
		color = mix(color, vec4(ccc(v_fogColor.rgb,dfog,nfog,rain), cm), cm * smoothstep(1.0, 0.95, length(ajp.xz)) * step(0.0, ajp.y));

	vec3 curr = unchartedModified(color.rgb * 5.0);
	vec3 ws = 1.0 / unchartedModified(vec3_splat(12.0));
		curr *= ws;
	color.rgb = pow(curr, vec3_splat(1.0 / 2.2));
	color.rgb = saturate(color.rgb);
	
    float gray = luma(color.rgb);
    color.rgb = mix(vec3_splat(gray), color.rgb, 1.1);
    gl_FragColor = color;
}
