package dev.quantumfusion.dashloader.mixin.main;

import dev.quantumfusion.dashloader.DashLoader;
import dev.quantumfusion.dashloader.ProfilerHandler;
import dev.quantumfusion.dashloader.ProgressHandler;
import dev.quantumfusion.dashloader.client.DashToast;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

import static dev.quantumfusion.dashloader.DashLoader.INSTANCE;


@Mixin(value = SplashOverlay.class, priority = 69420)
public class SplashScreenMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@Shadow
	private long reloadCompleteTime;

	@Shadow
	@Final
	private ResourceReload reload;

	@Mutable
	@Shadow
	@Final
	private boolean reloading;

	@Inject(
			method = "render(Lnet/minecraft/client/util/math/MatrixStack;IIF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMeasuringTimeMs()J", shift = At.Shift.BEFORE, ordinal = 1)
	)
	private void done(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		this.client.setOverlay(null);
		if (this.client.currentScreen != null) {
			if (this.client.currentScreen instanceof TitleScreen) {
				this.client.currentScreen = new TitleScreen(false);
			}
		}

		ProfilerHandler.INSTANCE.print();

		if (INSTANCE.isWrite() && client.getToastManager().getToast(DashToast.class, Toast.TYPE) == null) {
			// Yes this is bad. But it makes us not require Fabric API
			var langCode = MinecraftClient.getInstance().getLanguageManager().getLanguage().getCode();
			DashLoader.LOG.info(langCode);
			var stream = this.getClass().getClassLoader().getResourceAsStream("dashloader/lang/" + langCode + ".json");
			var map = new HashMap<String, String>();
			if (stream != null) {
				DashLoader.LOG.info("Found translations");
				Language.load(stream, map::put);
			} else {
				var en_stream = this.getClass().getClassLoader().getResourceAsStream("dashloader/lang/en_us.json");
				if (en_stream != null) {
					Language.load(en_stream, map::put);
				}
			}
			DashLoader.LOG.info("Missing translations");
			ProgressHandler.INSTANCE.setTranslations(map);
			client.getToastManager().add(new DashToast());
		}
	}

	@Inject(
			method = "render(Lnet/minecraft/client/util/math/MatrixStack;IIF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/resource/ResourceReload;isComplete()Z", shift = At.Shift.BEFORE)
	)
	private void removeMinimumTime(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (this.reloadCompleteTime == -1L && this.reload.isComplete()) {
			this.reloading = false;
		}
	}
}
