package dev.notalpha.dashloader.client.sprite;

import dev.notalpha.dashloader.mixin.accessor.SpriteAnimationAccessor;
import dev.notalpha.dashloader.registry.RegistryReader;
import dev.notalpha.dashloader.registry.RegistryWriter;
import dev.quantumfusion.hyphen.scan.annotations.DataNullable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.Sprite.AnimationFrame;

public final class DashSpriteAnimation {
	public final List<DashSpriteAnimationFrame> frames;
	public final int frameCount;
	@DataNullable
	public final DashSpriteInterpolation interpolation;

	public DashSpriteAnimation(
			List<DashSpriteAnimationFrame> frames,
			int frameCount,
			DashSpriteInterpolation interpolation) {
		this.frames = frames;
		this.frameCount = frameCount;
		this.interpolation = interpolation;
	}


	public DashSpriteAnimation(Sprite.Animation animation, RegistryWriter registry) {
		SpriteAnimationAccessor access = ((SpriteAnimationAccessor) animation);
		this.frames = new ArrayList<>();
		for (var frame : access.getFrames()) {
			this.frames.add(new DashSpriteAnimationFrame(frame));
		}
		this.frameCount = access.getFrameCount();
		this.interpolation = access.getInterpolation() == null ? null : new DashSpriteInterpolation(access.getInterpolation(), registry);
	}


	public Sprite.Animation export(Sprite owner, RegistryReader registry) {
		var framesOut = new ArrayList<AnimationFrame>();
		for (var frame : this.frames) {
			framesOut.add(frame.export(registry));
		}

		return SpriteAnimationAccessor.init(
				owner,
				framesOut,
				this.frameCount,
				this.interpolation == null ? null : this.interpolation.export(owner, registry)
		);
	}
}
