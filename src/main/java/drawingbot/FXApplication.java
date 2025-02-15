package drawingbot;

import drawingbot.api.API;
import drawingbot.api.IPlugin;
import drawingbot.api_impl.DrawingBotV3API;
import drawingbot.files.json.AbstractJsonLoader;
import drawingbot.files.json.projects.ObservableProject;
import drawingbot.files.LoggingHandler;
import drawingbot.files.json.JsonLoaderManager;
import drawingbot.javafx.FXHelper;
import drawingbot.javafx.preferences.DBPreferences;
import drawingbot.registry.MasterRegistry;
import drawingbot.registry.Register;
import drawingbot.render.IDisplayMode;
import drawingbot.render.jfx.JavaFXRenderer;
import drawingbot.render.opengl.OpenGLRendererImpl;
import drawingbot.render.overlays.*;
import drawingbot.utils.AbstractSoftware;
import drawingbot.utils.LazyTimer;
import drawingbot.javafx.util.MouseMonitor;
import drawingbot.utils.Utils;
import drawingbot.utils.flags.Flags;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.logging.Level;

public class FXApplication extends Application {

    public static FXApplication INSTANCE;
    public static AbstractSoftware software;
    public static String[] launchArgs = new String[0];
    public static Stage primaryStage;
    public static Scene primaryScene;
    public static List<Stage> childStages = new ArrayList<>();
    public static DrawTimer drawTimer;
    public static boolean isPremiumEnabled;
    public static boolean isHeadless;

    public static SimpleBooleanProperty isLoaded = new SimpleBooleanProperty(false);
    public static MouseMonitor mouseMonitor;

    public static boolean isDeveloperMode = false;

    public static void main(String[] args) {
        launchArgs = args;

        // Setup console / file logging
        LoggingHandler.init();

        SplashScreen.initPreloader();
        launch(args);
    }

    public static class InitialLoadTask extends Task<Boolean> {

        @Override
        protected void setException(Throwable t) {
            super.setException(t);
            DrawingBotV3.logger.log(Level.SEVERE, "LOAD TASK FAILED", t);
        }

        @Override
        protected Boolean call() throws Exception {
            DrawingBotV3.logger.entering("FXApplication", "start");

            ///////////////////////////////////////////////////////////////////////////////////////////////////////

            ///// PRE INIT \\\\\\

            DrawingBotV3.logger.info("Plugins: Finding Plugins");
            MasterRegistry.findPlugins();

            DrawingBotV3.logger.info("Plugins: Found " + MasterRegistry.PLUGINS.size() + " Plugins");
            MasterRegistry.PLUGINS.forEach(plugin -> DrawingBotV3.logger.info("Plugin: " + plugin.getPluginName()));

            DrawingBotV3.logger.info("Plugins: Pre-Init");
            MasterRegistry.PLUGINS.forEach(IPlugin::preInit);

            DrawingBotV3.logger.info("Json Loaders: Init");
            MasterRegistry.INSTANCE.presetLoaders.forEach(AbstractJsonLoader::init);

            DrawingBotV3.logger.info("DrawingBotV3: Loading Configuration");
            JsonLoaderManager.loadConfigFiles();

            DrawingBotV3.logger.info("DrawingBotV3: Loading API");
            API.INSTANCE = new DrawingBotV3API();

            DrawingBotV3.logger.info("Master Registry: Init");
            MasterRegistry.init();

            DrawingBotV3.logger.info("Renderers: Pre-Init");
            DrawingBotV3.RENDERER = new JavaFXRenderer(Screen.getPrimary().getBounds());
            DrawingBotV3.OPENGL_RENDERER = new OpenGLRendererImpl(Screen.getPrimary().getBounds());

            ///////////////////////////////////////////////////////////////////////////////////////////////////////

            ///// INIT \\\\\

            DrawingBotV3.logger.info("DrawingBotV3: Init");
            DrawingBotV3.INSTANCE = new DrawingBotV3();
            DrawingBotV3.INSTANCE.init();

            DrawingBotV3.logger.info("DrawingBotV3: Loading Dummy Project");
            DrawingBotV3.INSTANCE.activeProject.set(new ObservableProject());
            DrawingBotV3.INSTANCE.activeProjects.add(DrawingBotV3.INSTANCE.activeProject.get());

            MasterRegistry.postInit();
            DBPreferences.INSTANCE.postInit();

            DrawingBotV3.logger.info("Json Loader: Load JSON Files");
            JsonLoaderManager.loadJSONFiles();

            DrawingBotV3.logger.info("DrawingBotV3: Registering Missing Presets");
            MasterRegistry.INSTANCE.registerMissingDefaultPFMPresets();

            if(Utils.getOS().isMac()){
                //JavaFX LCD Font Smoothing looks good on windows, but bad on MacOS, so disable it.
                System.setProperty("prism.lcdtext", "false");
            }

            DrawingBotV3.logger.info("DrawingBotV3: Loading User Interface");
            CountDownLatch latchA = new CountDownLatch(1);
            Platform.runLater(() -> {
                FXMLLoader loader = new FXMLLoader(FXApplication.class.getResource("userinterface.fxml"));
                Parent root = null;
                try {
                    root = loader.load();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                DrawingBotV3.INSTANCE.controller = loader.getController();
                DrawingBotV3.INSTANCE.controller.initSeparateStages();
                DrawingBotV3.INSTANCE.controller.setupBindings();

                Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
                FXApplication.primaryScene = new Scene(root, visualBounds.getWidth(), visualBounds.getHeight(), false, SceneAntialiasing.BALANCED);
                DBPreferences.INSTANCE.uiWindowSize.get().setupStage(primaryStage);

                if(!isHeadless) {
                    primaryStage.setScene(primaryScene);
                }
                latchA.countDown();
            });

            latchA.await();

            //// ADD ACCELERATORS \\\\
            int keypad = 1;
            for(IDisplayMode displayMode : MasterRegistry.INSTANCE.displayModes){
                FXApplication.primaryScene.getAccelerators().put(KeyCombination.valueOf("Shift + " + keypad), () -> DrawingBotV3.INSTANCE.displayMode.set(displayMode));
                keypad++;
            }

            FXApplication.primaryScene.getAccelerators().put(KeyCombination.valueOf("Shift + V"), () -> DrawingBotV3.INSTANCE.controller.versionControlController.saveVersion());

            CountDownLatch latchB = new CountDownLatch(1);
            Platform.runLater(() -> {
                DrawingBotV3.logger.info("Renderers: Init JFX Renderer");
                DrawingBotV3.RENDERER.init();

                DrawingBotV3.logger.info("Renderers: Init OpenGL Renderer");
                DrawingBotV3.OPENGL_RENDERER.init();

                DrawingBotV3.logger.info("Renderers: Load Display Mode");
                DrawingBotV3.INSTANCE.displayMode.get().applySettings();
                DrawingBotV3.INSTANCE.displayMode.addListener((observable, oldValue, newValue) -> {
                    if(oldValue == null || newValue.getRenderer() == oldValue.getRenderer()){
                        DrawingBotV3.project().setRenderFlag(Flags.FORCE_REDRAW, true);
                    }
                    if(oldValue == null || newValue.getRenderer() != oldValue.getRenderer()){
                        DrawingBotV3.project().setRenderFlag(Flags.CHANGED_RENDERER, true);
                    }
                    DrawingBotV3.INSTANCE.onDisplayModeChanged(oldValue, newValue);
                });

                DrawingBotV3.logger.info("Renderers: Load Overlays");
                MasterRegistry.INSTANCE.overlays.forEach(AbstractOverlay::init);

                //FXProgramSettings.init();

                //save the default UI State before applying the users own defaults
                FXHelper.saveDefaultUIStates();

                DrawingBotV3.logger.info("Json Loader: Load Defaults");
                JsonLoaderManager.loadDefaults();
                latchB.countDown();
            });
            latchB.await();

            DrawingBotV3.logger.info("Plugins: Post Init");
            MasterRegistry.PLUGINS.forEach(IPlugin::postInit);

            if(!isHeadless){
                CountDownLatch latchC = new CountDownLatch(1);
                DrawingBotV3.logger.info("Plugins: Load JFX Stages");
                Platform.runLater(() -> {
                    for(IPlugin plugin : MasterRegistry.PLUGINS){
                        plugin.loadJavaFXStages();
                    }
                    DrawingBotV3.logger.info("DrawingBotV3: Loading Event Handlers");
                    FXApplication.primaryScene.addEventHandler(MouseEvent.MOUSE_MOVED, mouseMonitor = new MouseMonitor());

                    latchC.countDown();
                });

                latchC.await();
            }

            ///////////////////////////////////////////////////////////////////////////////////////////////////////
            //secondary config load, shouldn't cause any clashes, ensure things like UI elements are setup correctly
            DrawingBotV3.logger.info("DrawingBotV3: Loading User Preferences");
            JsonLoaderManager.loadConfigFiles();

            if(!isHeadless) {
                DrawingBotV3.logger.info("DrawingBotV3: Loading User Interface");
                CountDownLatch latchD = new CountDownLatch(1);
                Platform.runLater(() -> {
                    DrawingBotV3.INSTANCE.controller.viewportScrollPane.addEventHandler(MouseEvent.MOUSE_MOVED, DrawingBotV3.INSTANCE::onMouseMovedViewport);
                    DrawingBotV3.INSTANCE.controller.viewportScrollPane.addEventHandler(MouseEvent.MOUSE_PRESSED, DrawingBotV3.INSTANCE::onMousePressedViewport);
                    DrawingBotV3.INSTANCE.controller.viewportScrollPane.addEventHandler(KeyEvent.KEY_PRESSED, DrawingBotV3.INSTANCE::onKeyPressedViewport);
                    DrawingBotV3.INSTANCE.projectName.set("Untitled");
                    DrawingBotV3.INSTANCE.resetView();
                    primaryStage.titleProperty().bind(Bindings.createStringBinding(() -> FXApplication.getSoftware().getDisplayName() + ", Version: " + FXApplication.getSoftware().getDisplayVersion() + ", " + "'" + DrawingBotV3.INSTANCE.projectNameBinding.get() + "'", DrawingBotV3.INSTANCE.projectNameBinding));
                    primaryStage.setResizable(true);
                    applyTheme(primaryStage);
                    primaryStage.show();
                    latchD.countDown();

                    // set up main drawing loop
                    drawTimer = new DrawTimer(FXApplication.INSTANCE);
                    drawTimer.start();
                });

                latchD.await();
            }

            ///////////////////////////////////////////////////////////////////////////////////////////////////////

            if(launchArgs.length >= 1){
                DrawingBotV3.logger.info("Attempting to load file at startup");
                try {
                    File startupFile =  new File(launchArgs[0]);
                    DrawingBotV3.INSTANCE.openFile(DrawingBotV3.context(), startupFile, false, false);
                } catch (Exception e) {
                    DrawingBotV3.logger.log(Level.SEVERE, "Failed to load file at startup", e);
                }
            }

            DrawingBotV3.logger.info("DrawingBotV3: Loaded");
            SplashScreen.stopPreloader(FXApplication.INSTANCE);
            isLoaded.set(true);

            ///////////////////////////////////////////////////////////////////////////////////////////////////////

            DrawingBotV3.logger.exiting("FXApplication", "start");
            return null;
        }
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        INSTANCE = this;
        FXApplication.primaryStage = primaryStage;
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            DrawingBotV3.logger.log(Level.SEVERE, e, e::getMessage);
        });

        ///////////////////////////////////////////////////////////////////////////////////////////////////////

        ///// SETUP OUTPUTS \\\\\
        SplashScreen.startPreloader(this);
    }

    public static AbstractSoftware getSoftware(){
        return software;
    }

    public static void setSoftware(AbstractSoftware theme){
        software = theme;
    }

    public static void applyTheme(Stage primaryStage){
        software.applyThemeToStage(primaryStage);
        software.applyThemeToScene(primaryStage.getScene());
    }

    public static void applyCurrentTheme(){
        software.applyThemeToScene(primaryScene);
        childStages.forEach(stage -> software.applyThemeToScene(stage.getScene()));
    }

    public void onFirstTick(){
        //NOP
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        Register.PRESET_LOADER_CONFIGS.markDirty();
        LoggingHandler.saveLoggingFiles();
    }

    public static class DrawTimer extends AnimationTimer{

        public final FXApplication fxApplication;
        private final LazyTimer timer = new LazyTimer();

        private boolean isFirstTick = true;

        public DrawTimer(FXApplication fxApplication){
            this.fxApplication = fxApplication;
        }

        @Override
        public void handle(long now) {
            if(isFirstTick){
                DrawingBotV3.INSTANCE.resetView();
                isFirstTick = false;
                fxApplication.onFirstTick();
                return;
            }

            timer.start();
            DrawingBotV3.project().displayMode.get().getRenderer().preRender();
            MasterRegistry.INSTANCE.overlays.forEach(o -> {
                if(o.isActive()){
                    o.preRender();
                }
            });

            DrawingBotV3.project().displayMode.get().getRenderer().doRender();
            MasterRegistry.INSTANCE.overlays.forEach(o -> {
                if(o.isActive()){
                    o.doRender();
                }
            });

            DrawingBotV3.INSTANCE.tick();

            DrawingBotV3.project().displayMode.get().getRenderer().postRender();
            MasterRegistry.INSTANCE.overlays.forEach(o -> {
                if(o.isActive()){
                    o.postRender();
                }
            });


            timer.finish();

            if(!DrawingBotV3.project().displayMode.get().getRenderer().isOpenGL() && timer.getElapsedTime() > 1000/60){
                DrawingBotV3.logger.finest("RENDERER TOOK: " + timer.getElapsedTimeFormatted() + " milliseconds" + " expected " + 1000/60);
            }
        }
    }
}
