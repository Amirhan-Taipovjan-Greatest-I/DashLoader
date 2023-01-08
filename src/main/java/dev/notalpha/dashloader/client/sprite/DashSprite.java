package dev.notalpha.dashloader.client.sprite;

import dev.notalpha.dashloader.api.DashObject;
import dev.notalpha.dashloader.misc.UnsafeHelper;
import dev.notalpha.dashloader.mixin.accessor.MipmapHelperAccessor;
import dev.notalpha.dashloader.mixin.accessor.NativeImageAccessor;
import dev.notalpha.dashloader.mixin.accessor.SpriteAccessor;
import dev.notalpha.dashloader.registry.RegistryReader;
import dev.notalpha.dashloader.registry.RegistryWriter;
import dev.quantumfusion.hyphen.scan.annotations.DataNullable;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;

public class DashSprite implements DashObject<Sprite> {
	@DataNullable
	public final DashSpriteAnimation animation;
	public final int image;
	public final boolean imageTransparent;
	public final int images;
	public final int x;
	public final int y;
	public final int width;
	public final int height;
	public final float uMin;
	public final float uMax;
	public final float vMin;
	public final float vMax;

	public DashSprite(DashSpriteAnimation animation,
						  int image,
						  boolean imageTransparent,
						  int images,
						  int x, int y, int width, int height,
						  float uMin, float uMax, float vMin, float vMax
	) {
		this.animation = animation;
		this.image = image;
		this.imageTransparent = imageTransparent;
		this.images = images;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.uMin = uMin;
		this.uMax = uMax;
		this.vMin = vMin;
		this.vMax = vMax;
	}

	public DashSprite(Sprite sprite, RegistryWriter writer) {
		this.animation = sprite.getAnimation() == null ? null : new DashSpriteAnimation((Sprite.Animation) sprite.getAnimation(), writer);

		NativeImage[] images = ((SpriteAccessor) sprite).getImages();
		NativeImage image = images[0];
		this.image = writer.add(image);

		boolean transparent = false;
		check:
		for (int x = 0; x < image.getWidth(); x++) {
			for (int y = 0; y < image.getHeight(); y++) {
				if (image.getColor(x, y) >> 24 == 0) {
					transparent = true;
					break check;
				}
			}
		}
		this.imageTransparent = transparent;
		this.images = images.length;

		this.x = sprite.getX();
		this.y = sprite.getY();
		this.width = sprite.getWidth();
		this.height = sprite.getHeight();
		this.uMin = sprite.getMinU();
		this.uMax = sprite.getMaxU();
		this.vMin = sprite.getMinV();
		this.vMax = sprite.getMaxV();
	}

	@Override
	public Sprite export(final RegistryReader registry) {
		final Sprite out = UnsafeHelper.allocateInstance(Sprite.class);
		final SpriteAccessor spriteAccessor = ((SpriteAccessor) out);


		final NativeImage[] images = new NativeImage[this.images];
		images[0] = registry.get(this.image);
		for (int i = 1; i <= (this.images - 1); ++i) {
			final NativeImage oldLevel = images[i - 1];
			//noinspection resource
			final NativeImage newLevel = new NativeImage(oldLevel.getWidth() >> 1, oldLevel.getHeight() >> 1, false);
			final int newWidth = newLevel.getWidth();
			final int newHeight = newLevel.getHeight();

			final long oldPtr = ((NativeImageAccessor)(Object) oldLevel).getPointer();
			final long newPtr = ((NativeImageAccessor)(Object) newLevel).getPointer();

			final int oldWidth = oldLevel.getWidth();
			for (int x = 0; x < newWidth; ++x) {
				for (int y = 0; y < newHeight; ++y) {
					final int one = getColorUnsafe(oldPtr, oldWidth, x * 2, y * 2);
					final int two = getColorUnsafe(oldPtr, oldWidth, x * 2 + 1, y * 2);
					final int three = getColorUnsafe(oldPtr, oldWidth, x * 2, y * 2 + 1);
					final int four = getColorUnsafe(oldPtr, oldWidth, x * 2 + 1, y * 2 + 1);
					final int color = MipmapHelperAccessor.blend(one, two, three, four, this.imageTransparent);
					setColorUnsafe(newPtr, newWidth, x, y, color);
				}
			}

			images[i] = newLevel;
		}

		spriteAccessor.setImages(images);
		spriteAccessor.setX(this.x);
		spriteAccessor.setY(this.y);
		spriteAccessor.setWidth(this.width);
		spriteAccessor.setHeight(this.height);
		spriteAccessor.setUMin(this.uMin);
		spriteAccessor.setUMax(this.uMax);
		spriteAccessor.setVMin(this.vMin);
		spriteAccessor.setVMax(this.vMax);
		spriteAccessor.setAnimation(this.animation == null ? null : this.animation.export(out, registry));
		return out;
	}

	private static int getColorUnsafe(final long ptr, final int oldWidth, final int x, final int y) {
		return UnsafeHelper.UNSAFE.getInt(ptr + (((long)x + (long)y * (long)oldWidth) * 4L));
	}

	private static void setColorUnsafe(final long ptr, final int width, final int x, final int y, final int color) {
		UnsafeHelper.UNSAFE.putInt(ptr + (((long)x + (long)y * (long)width) * 4L), color);
	}
}
