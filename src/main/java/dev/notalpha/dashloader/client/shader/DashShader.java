package dev.notalpha.dashloader.client.shader;

import com.google.common.collect.UnmodifiableIterator;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.notalpha.dashloader.api.DashObject;
import dev.notalpha.dashloader.misc.UnsafeHelper;
import dev.notalpha.dashloader.mixin.accessor.ShaderProgramAccessor;
import dev.notalpha.dashloader.registry.RegistryReader;
import dev.quantumfusion.hyphen.scan.annotations.DataNullable;
import dev.quantumfusion.hyphen.scan.annotations.DataSubclasses;
import net.minecraft.client.gl.GlProgramManager;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;

import java.util.*;

public final class DashShader implements DashObject<ShaderProgram> {
	public final Map<String, Sampler> samplers;
	public final String name;
	public final DashGlBlendState blendState;
	public final List<String> attributeNames;
	public final DashShaderStage vertexShader;
	public final DashShaderStage fragmentShader;
	public final VertexFormatsHelper.Value format;
	public final Map<String, DashGlUniform> loadedUniforms;
	public final List<String> samplerNames;
	public transient ShaderProgram toApply;

	public DashShader(Map<String, Sampler> samplers, String name, DashGlBlendState blendState, List<String> attributeNames, DashShaderStage vertexShader, DashShaderStage fragmentShader, VertexFormatsHelper.Value format, Map<String, DashGlUniform> loadedUniforms, List<String> samplerNames) {
		this.samplers = samplers;
		this.name = name;
		this.blendState = blendState;
		this.attributeNames = attributeNames;
		this.vertexShader = vertexShader;
		this.fragmentShader = fragmentShader;
		this.format = format;
		this.loadedUniforms = loadedUniforms;
		this.samplerNames = samplerNames;
	}

	public DashShader(ShaderProgram shader) {
		ShaderProgramAccessor shaderAccess = (ShaderProgramAccessor) shader;

		this.samplers = new LinkedHashMap<>();
		shaderAccess.getSamplers().forEach((s, o) -> this.samplers.put(s, new Sampler(o)));
		this.name = shader.getName();

		this.blendState = new DashGlBlendState(shaderAccess.getBlendState());
		this.attributeNames = shaderAccess.getAttributeNames();
		this.vertexShader = new DashShaderStage(shader.getVertexShader());
		this.fragmentShader = new DashShaderStage(shader.getFragmentShader());
		this.format = VertexFormatsHelper.getEnum(shader.getFormat());
		this.loadedUniforms = new HashMap<>();
		shaderAccess.getLoadedUniforms().forEach((s, glUniform) -> this.loadedUniforms.put(s, new DashGlUniform(glUniform)));
		this.samplerNames = shaderAccess.getSamplerNames();
	}


	@Override
	public ShaderProgram export(RegistryReader reader) {
		this.toApply = UnsafeHelper.allocateInstance(ShaderProgram.class);
		ShaderProgramAccessor shaderAccess = (ShaderProgramAccessor) this.toApply;
		//object init
		shaderAccess.setLoadedSamplerIds(new ArrayList<>());
		shaderAccess.setLoadedUniformIds(new ArrayList<>());
		shaderAccess.setLoadedUniforms(new HashMap<>());
		final ArrayList<GlUniform> uniforms = new ArrayList<>();
		shaderAccess.setUniforms(uniforms);
		List<Integer> loadedAttributeIds = new ArrayList<>();
		shaderAccess.setLoadedAttributeIds(loadedAttributeIds);

		shaderAccess.setSamplerNames(this.samplerNames);

		//<init> top
		shaderAccess.setName(this.name);
		final VertexFormat format = this.format.getFormat();
		shaderAccess.setFormat(format);


		//JsonHelper.getArray(jsonObject, "samplers", (JsonArray)null)
		var samplersOut = new HashMap<String, Object>();
		this.samplers.forEach((s, o) -> samplersOut.put(s, o.sampler));
		shaderAccess.setSamplers(samplersOut);

		// JsonHelper.getArray(jsonObject, "attributes", (JsonArray)null);
		shaderAccess.setAttributeNames(this.attributeNames);

		var uniformsOut = new HashMap<String, GlUniform>();

		this.loadedUniforms.forEach((s, dashGlUniform) -> {
			GlUniform uniform = dashGlUniform.export(this.toApply);
			uniforms.add(uniform);
			uniformsOut.put(s, uniform);
		});

		// JsonHelper.getArray(jsonObject, "uniforms", (JsonArray)null);
		final GlUniform modelViewMatOut = uniformsOut.get("ModelViewMat");
		final GlUniform projectionMatOut = uniformsOut.get("ProjMat");
		final GlUniform textureMatOut = uniformsOut.get("TextureMat");
		final GlUniform screenSizeOut = uniformsOut.get("ScreenSize");
		final GlUniform colorModulatorOut = uniformsOut.get("ColorModulator");
		final GlUniform light0DirectionOut = uniformsOut.get("Light0_Direction");
		final GlUniform light1DirectionOut = uniformsOut.get("Light1_Direction");
		final GlUniform fogStartOut = uniformsOut.get("FogStart");
		final GlUniform fogEndOut = uniformsOut.get("FogEnd");
		final GlUniform fogColorOut = uniformsOut.get("FogColor");
		final GlUniform lineWidthOut = uniformsOut.get("LineWidth");
		final GlUniform gameTimeOut = uniformsOut.get("GameTime");
		final GlUniform chunkOffsetOut = uniformsOut.get("ChunkOffset");

		this.toApply.markUniformsDirty();
		this.toApply.modelViewMat = modelViewMatOut;
		this.toApply.projectionMat = projectionMatOut;
		this.toApply.textureMat = textureMatOut;
		this.toApply.screenSize = screenSizeOut;
		this.toApply.colorModulator = colorModulatorOut;
		this.toApply.light0Direction = light0DirectionOut;
		this.toApply.light1Direction = light1DirectionOut;
		this.toApply.fogStart = fogStartOut;
		this.toApply.fogEnd = fogEndOut;
		this.toApply.fogColor = fogColorOut;
		this.toApply.lineWidth = lineWidthOut;
		this.toApply.gameTime = gameTimeOut;
		this.toApply.chunkOffset = chunkOffsetOut;
		return this.toApply;
	}


	@Override
	public void postExport(RegistryReader reader) {
		ShaderProgramAccessor shaderAccess = (ShaderProgramAccessor) this.toApply;
		shaderAccess.setBlendState(this.blendState.export());
		shaderAccess.setVertexShader(this.vertexShader.exportProgram());
		shaderAccess.setFragmentShader(this.fragmentShader.exportProgram());
		final List<Integer> loadedAttributeIds = shaderAccess.getLoadedAttributeIds();

		final int programId = GlStateManager.glCreateProgram();
		shaderAccess.setGlRef(programId);


		if (this.attributeNames != null) {
			int l = 0;
			for (UnmodifiableIterator<String> var35 = this.format.getFormat().getAttributeNames().iterator(); var35.hasNext(); ++l) {
				String string3 = var35.next();
				GlUniform.bindAttribLocation(programId, l, string3);
				loadedAttributeIds.add(l);
			}
		}
		GlProgramManager.linkProgram(this.toApply);
		shaderAccess.loadref();
	}

	public static class Sampler {
		@DataNullable
		@DataSubclasses({Integer.class, String.class})
		public final Object sampler;

		public Sampler(Object sampler) {
			this.sampler = sampler;
		}
	}

}
