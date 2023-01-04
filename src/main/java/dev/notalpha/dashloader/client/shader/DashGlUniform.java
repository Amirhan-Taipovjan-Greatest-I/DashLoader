package dev.notalpha.dashloader.client.shader;

import dev.notalpha.dashloader.io.IOHelper;
import dev.notalpha.dashloader.misc.UnsafeHelper;
import dev.notalpha.dashloader.mixin.accessor.GlUniformAccessor;
import dev.quantumfusion.hyphen.scan.annotations.DataNullable;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;

import java.util.List;

public final class DashGlUniform {
	public final int count;
	public final int dataType;

	public final int @DataNullable [] intData;
	public final float @DataNullable [] floatData;
	public final String name;


	public DashGlUniform(int count, int dataType, int[] intData, float[] floatData, String name) {
		this.count = count;
		this.dataType = dataType;
		this.intData = intData;
		this.floatData = floatData;
		this.name = name;
	}

	public DashGlUniform(GlUniform glUniform) {
		this.count = glUniform.getCount();
		this.dataType = glUniform.getDataType();
		this.intData = glUniform.getIntData() == null ? null : IOHelper.toArray(glUniform.getIntData());
		this.floatData = glUniform.getFloatData() == null ? null : IOHelper.toArray(glUniform.getFloatData());
		this.name = glUniform.getName();
	}

	public GlUniform export(ShaderProgram shader) {
		return new GlUniform(this.name, this.dataType, this.count, shader);
	}
}
