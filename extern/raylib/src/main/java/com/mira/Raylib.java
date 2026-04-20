package com.mira;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import static com.raylib.Colors.BEIGE;
import static com.raylib.Colors.BLACK;
import static com.raylib.Colors.BLUE;
import static com.raylib.Colors.BROWN;
import static com.raylib.Colors.DARKBLUE;
import static com.raylib.Colors.DARKBROWN;
import static com.raylib.Colors.DARKGRAY;
import static com.raylib.Colors.DARKGREEN;
import static com.raylib.Colors.DARKPURPLE;
import static com.raylib.Colors.GOLD;
import static com.raylib.Colors.GRAY;
import static com.raylib.Colors.GREEN;
import static com.raylib.Colors.LIGHTGRAY;
import static com.raylib.Colors.LIME;
import static com.raylib.Colors.MAGENTA;
import static com.raylib.Colors.MAROON;
import static com.raylib.Colors.ORANGE;
import static com.raylib.Colors.PINK;
import static com.raylib.Colors.PURPLE;
import static com.raylib.Colors.RAYWHITE;
import static com.raylib.Colors.RED;
import static com.raylib.Colors.SKYBLUE;
import static com.raylib.Colors.VIOLET;
import static com.raylib.Colors.WHITE;
import static com.raylib.Colors.YELLOW;
import static com.raylib.Raylib.BeginBlendMode;
import static com.raylib.Raylib.BeginDrawing;
import static com.raylib.Raylib.BeginMode2D;
import static com.raylib.Raylib.BeginMode3D;
import static com.raylib.Raylib.BeginScissorMode;
import static com.raylib.Raylib.BeginShaderMode;
import static com.raylib.Raylib.BeginTextureMode;
import com.raylib.Raylib.BoundingBox;
import static com.raylib.Raylib.CAMERA_PERSPECTIVE;
import com.raylib.Raylib.Camera2D;
import com.raylib.Raylib.Camera3D;
import static com.raylib.Raylib.CheckCollisionCircleRec;
import static com.raylib.Raylib.CheckCollisionCircles;
import static com.raylib.Raylib.CheckCollisionPointCircle;
import static com.raylib.Raylib.CheckCollisionPointRec;
import static com.raylib.Raylib.CheckCollisionPointTriangle;
import static com.raylib.Raylib.CheckCollisionRecs;
import static com.raylib.Raylib.ClearBackground;
import static com.raylib.Raylib.CloseAudioDevice;
import static com.raylib.Raylib.CloseWindow;
import com.raylib.Raylib.Color;
import static com.raylib.Raylib.ColorAlpha;
import static com.raylib.Raylib.ColorToInt;
import static com.raylib.Raylib.DirectoryExists;
import static com.raylib.Raylib.DisableCursor;
import static com.raylib.Raylib.DrawBillboard;
import static com.raylib.Raylib.DrawBoundingBox;
import static com.raylib.Raylib.DrawCapsule;
import static com.raylib.Raylib.DrawCapsuleWires;
import static com.raylib.Raylib.DrawCircle;
import static com.raylib.Raylib.DrawCircleGradient;
import static com.raylib.Raylib.DrawCircleLines;
import static com.raylib.Raylib.DrawCircleLinesV;
import static com.raylib.Raylib.DrawCircleSector;
import static com.raylib.Raylib.DrawCircleSectorLines;
import static com.raylib.Raylib.DrawCircleV;
import static com.raylib.Raylib.DrawCube;
import static com.raylib.Raylib.DrawCubeV;
import static com.raylib.Raylib.DrawCubeWires;
import static com.raylib.Raylib.DrawCubeWiresV;
import static com.raylib.Raylib.DrawCylinder;
import static com.raylib.Raylib.DrawCylinderEx;
import static com.raylib.Raylib.DrawCylinderWires;
import static com.raylib.Raylib.DrawCylinderWiresEx;
import static com.raylib.Raylib.DrawEllipse;
import static com.raylib.Raylib.DrawEllipseLines;
import static com.raylib.Raylib.DrawFPS;
import static com.raylib.Raylib.DrawGrid;
import static com.raylib.Raylib.DrawLine;
import static com.raylib.Raylib.DrawLine3D;
import static com.raylib.Raylib.DrawLineBezier;
import static com.raylib.Raylib.DrawLineEx;
import static com.raylib.Raylib.DrawPixel;
import static com.raylib.Raylib.DrawPixelV;
import static com.raylib.Raylib.DrawPlane;
import static com.raylib.Raylib.DrawPoint3D;
import static com.raylib.Raylib.DrawPoly;
import static com.raylib.Raylib.DrawPolyLines;
import static com.raylib.Raylib.DrawPolyLinesEx;
import static com.raylib.Raylib.DrawRay;
import static com.raylib.Raylib.DrawRectangle;
import static com.raylib.Raylib.DrawRectangleGradientEx;
import static com.raylib.Raylib.DrawRectangleGradientH;
import static com.raylib.Raylib.DrawRectangleGradientV;
import static com.raylib.Raylib.DrawRectangleLines;
import static com.raylib.Raylib.DrawRectangleLinesEx;
import static com.raylib.Raylib.DrawRectanglePro;
import static com.raylib.Raylib.DrawRectangleRec;
import static com.raylib.Raylib.DrawRectangleRounded;
import static com.raylib.Raylib.DrawRectangleRoundedLines;
import static com.raylib.Raylib.DrawRectangleRoundedLinesEx;
import static com.raylib.Raylib.DrawRectangleV;
import static com.raylib.Raylib.DrawRing;
import static com.raylib.Raylib.DrawRingLines;
import static com.raylib.Raylib.DrawSphere;
import static com.raylib.Raylib.DrawSphereEx;
import static com.raylib.Raylib.DrawSphereWires;
import static com.raylib.Raylib.DrawText;
import static com.raylib.Raylib.DrawTextEx;
import static com.raylib.Raylib.DrawTextPro;
import static com.raylib.Raylib.DrawTexture;
import static com.raylib.Raylib.DrawTextureEx;
import static com.raylib.Raylib.DrawTexturePro;
import static com.raylib.Raylib.DrawTextureRec;
import static com.raylib.Raylib.DrawTextureV;
import static com.raylib.Raylib.DrawTriangle;
import static com.raylib.Raylib.DrawTriangleLines;
import static com.raylib.Raylib.EnableCursor;
import static com.raylib.Raylib.EndBlendMode;
import static com.raylib.Raylib.EndDrawing;
import static com.raylib.Raylib.EndMode2D;
import static com.raylib.Raylib.EndMode3D;
import static com.raylib.Raylib.EndScissorMode;
import static com.raylib.Raylib.EndShaderMode;
import static com.raylib.Raylib.EndTextureMode;
import static com.raylib.Raylib.ExportImage;
import static com.raylib.Raylib.Fade;
import static com.raylib.Raylib.FileExists;
import com.raylib.Raylib.Font;
import static com.raylib.Raylib.GenImageColor;
import static com.raylib.Raylib.GenImageGradientLinear;
import static com.raylib.Raylib.GenImageGradientRadial;
import static com.raylib.Raylib.GenTextureMipmaps;
import static com.raylib.Raylib.GetCharPressed;
import static com.raylib.Raylib.GetClipboardText;
import static com.raylib.Raylib.GetCollisionRec;
import static com.raylib.Raylib.GetColor;
import static com.raylib.Raylib.GetCurrentMonitor;
import static com.raylib.Raylib.GetDirectoryPath;
import static com.raylib.Raylib.GetFPS;
import static com.raylib.Raylib.GetFileExtension;
import static com.raylib.Raylib.GetFileLength;
import static com.raylib.Raylib.GetFileModTime;
import static com.raylib.Raylib.GetFileName;
import static com.raylib.Raylib.GetFileNameWithoutExt;
import static com.raylib.Raylib.GetFontDefault;
import static com.raylib.Raylib.GetFrameTime;
import static com.raylib.Raylib.GetGamepadAxisCount;
import static com.raylib.Raylib.GetGamepadAxisMovement;
import static com.raylib.Raylib.GetKeyPressed;
import static com.raylib.Raylib.GetMasterVolume;
import static com.raylib.Raylib.GetMonitorCount;
import static com.raylib.Raylib.GetMonitorHeight;
import static com.raylib.Raylib.GetMonitorName;
import static com.raylib.Raylib.GetMonitorRefreshRate;
import static com.raylib.Raylib.GetMonitorWidth;
import static com.raylib.Raylib.GetMouseDelta;
import static com.raylib.Raylib.GetMousePosition;
import static com.raylib.Raylib.GetMouseWheelMove;
import static com.raylib.Raylib.GetMouseX;
import static com.raylib.Raylib.GetMouseY;
import static com.raylib.Raylib.GetMusicTimeLength;
import static com.raylib.Raylib.GetMusicTimePlayed;
import static com.raylib.Raylib.GetRandomValue;
import static com.raylib.Raylib.GetRayCollisionBox;
import static com.raylib.Raylib.GetRayCollisionQuad;
import static com.raylib.Raylib.GetRayCollisionSphere;
import static com.raylib.Raylib.GetRayCollisionTriangle;
import static com.raylib.Raylib.GetRenderHeight;
import static com.raylib.Raylib.GetRenderWidth;
import static com.raylib.Raylib.GetScreenHeight;
import static com.raylib.Raylib.GetScreenToWorld2D;
import static com.raylib.Raylib.GetScreenToWorldRay;
import static com.raylib.Raylib.GetScreenWidth;
import static com.raylib.Raylib.GetShaderLocation;
import static com.raylib.Raylib.GetShaderLocationAttrib;
import static com.raylib.Raylib.GetTime;
import static com.raylib.Raylib.GetTouchPointCount;
import static com.raylib.Raylib.GetTouchPosition;
import static com.raylib.Raylib.GetTouchX;
import static com.raylib.Raylib.GetTouchY;
import static com.raylib.Raylib.GetWorkingDirectory;
import static com.raylib.Raylib.GetWorldToScreen;
import static com.raylib.Raylib.GetWorldToScreen2D;
import static com.raylib.Raylib.HideCursor;
import com.raylib.Raylib.Image;
import static com.raylib.Raylib.ImageAlphaClear;
import static com.raylib.Raylib.ImageAlphaCrop;
import static com.raylib.Raylib.ImageAlphaMask;
import static com.raylib.Raylib.ImageAlphaPremultiply;
import static com.raylib.Raylib.ImageBlurGaussian;
import static com.raylib.Raylib.ImageClearBackground;
import static com.raylib.Raylib.ImageColorBrightness;
import static com.raylib.Raylib.ImageColorContrast;
import static com.raylib.Raylib.ImageColorGrayscale;
import static com.raylib.Raylib.ImageColorInvert;
import static com.raylib.Raylib.ImageColorTint;
import static com.raylib.Raylib.ImageCopy;
import static com.raylib.Raylib.ImageCrop;
import static com.raylib.Raylib.ImageDrawPixel;
import static com.raylib.Raylib.ImageDrawRectangle;
import static com.raylib.Raylib.ImageDrawText;
import static com.raylib.Raylib.ImageFlipHorizontal;
import static com.raylib.Raylib.ImageFlipVertical;
import static com.raylib.Raylib.ImageFormat;
import static com.raylib.Raylib.ImageResize;
import static com.raylib.Raylib.ImageResizeNN;
import static com.raylib.Raylib.ImageRotate;
import static com.raylib.Raylib.ImageToPOT;
import static com.raylib.Raylib.InitAudioDevice;
import static com.raylib.Raylib.InitWindow;
import static com.raylib.Raylib.IsAudioDeviceReady;
import static com.raylib.Raylib.IsCursorHidden;
import static com.raylib.Raylib.IsCursorOnScreen;
import static com.raylib.Raylib.IsFileDropped;
import static com.raylib.Raylib.IsFontValid;
import static com.raylib.Raylib.IsGamepadAvailable;
import static com.raylib.Raylib.IsGamepadButtonDown;
import static com.raylib.Raylib.IsGamepadButtonPressed;
import static com.raylib.Raylib.IsGamepadButtonReleased;
import static com.raylib.Raylib.IsKeyDown;
import static com.raylib.Raylib.IsKeyPressed;
import static com.raylib.Raylib.IsKeyPressedRepeat;
import static com.raylib.Raylib.IsKeyReleased;
import static com.raylib.Raylib.IsKeyUp;
import static com.raylib.Raylib.IsMouseButtonDown;
import static com.raylib.Raylib.IsMouseButtonPressed;
import static com.raylib.Raylib.IsMouseButtonReleased;
import static com.raylib.Raylib.IsMouseButtonUp;
import static com.raylib.Raylib.IsMusicStreamPlaying;
import static com.raylib.Raylib.IsShaderValid;
import static com.raylib.Raylib.IsSoundPlaying;
import static com.raylib.Raylib.IsWindowFocused;
import static com.raylib.Raylib.IsWindowFullscreen;
import static com.raylib.Raylib.IsWindowHidden;
import static com.raylib.Raylib.IsWindowMaximized;
import static com.raylib.Raylib.IsWindowMinimized;
import static com.raylib.Raylib.IsWindowReady;
import static com.raylib.Raylib.IsWindowResized;
import static com.raylib.Raylib.LoadFont;
import static com.raylib.Raylib.LoadImage;
import static com.raylib.Raylib.LoadImageFromTexture;
import static com.raylib.Raylib.LoadMusicStream;
import static com.raylib.Raylib.LoadRenderTexture;
import static com.raylib.Raylib.LoadShader;
import static com.raylib.Raylib.LoadShaderFromMemory;
import static com.raylib.Raylib.LoadSound;
import static com.raylib.Raylib.LoadSoundAlias;
import static com.raylib.Raylib.LoadSoundFromWave;
import static com.raylib.Raylib.LoadTexture;
import static com.raylib.Raylib.LoadTextureFromImage;
import static com.raylib.Raylib.LoadWave;
import static com.raylib.Raylib.MaximizeWindow;
import static com.raylib.Raylib.MeasureText;
import static com.raylib.Raylib.MeasureTextEx;
import static com.raylib.Raylib.MinimizeWindow;
import com.raylib.Raylib.Music;
import static com.raylib.Raylib.OpenURL;
import static com.raylib.Raylib.PauseMusicStream;
import static com.raylib.Raylib.PauseSound;
import static com.raylib.Raylib.PlayMusicStream;
import static com.raylib.Raylib.PlaySound;
import static com.raylib.Raylib.PollInputEvents;
import com.raylib.Raylib.Ray;
import com.raylib.Raylib.RayCollision;
import com.raylib.Raylib.Rectangle;
import com.raylib.Raylib.RenderTexture;
import static com.raylib.Raylib.RestoreWindow;
import static com.raylib.Raylib.ResumeMusicStream;
import static com.raylib.Raylib.ResumeSound;
import static com.raylib.Raylib.SeekMusicStream;
import static com.raylib.Raylib.SetClipboardText;
import static com.raylib.Raylib.SetConfigFlags;
import static com.raylib.Raylib.SetExitKey;
import static com.raylib.Raylib.SetMasterVolume;
import static com.raylib.Raylib.SetMouseCursor;
import static com.raylib.Raylib.SetMouseOffset;
import static com.raylib.Raylib.SetMousePosition;
import static com.raylib.Raylib.SetMouseScale;
import static com.raylib.Raylib.SetMusicPan;
import static com.raylib.Raylib.SetMusicPitch;
import static com.raylib.Raylib.SetMusicVolume;
import static com.raylib.Raylib.SetRandomSeed;
import static com.raylib.Raylib.SetShaderValue;
import static com.raylib.Raylib.SetShaderValueTexture;
import static com.raylib.Raylib.SetSoundPan;
import static com.raylib.Raylib.SetSoundPitch;
import static com.raylib.Raylib.SetSoundVolume;
import static com.raylib.Raylib.SetTargetFPS;
import static com.raylib.Raylib.SetTextLineSpacing;
import static com.raylib.Raylib.SetTextureFilter;
import static com.raylib.Raylib.SetTextureWrap;
import static com.raylib.Raylib.SetTraceLogLevel;
import static com.raylib.Raylib.SetWindowFocused;
import static com.raylib.Raylib.SetWindowMaxSize;
import static com.raylib.Raylib.SetWindowMinSize;
import static com.raylib.Raylib.SetWindowOpacity;
import static com.raylib.Raylib.SetWindowPosition;
import static com.raylib.Raylib.SetWindowSize;
import static com.raylib.Raylib.SetWindowTitle;
import com.raylib.Raylib.Shader;
import static com.raylib.Raylib.ShowCursor;
import com.raylib.Raylib.Sound;
import static com.raylib.Raylib.StopMusicStream;
import static com.raylib.Raylib.StopSound;
import static com.raylib.Raylib.SwapScreenBuffer;
import static com.raylib.Raylib.TakeScreenshot;
import com.raylib.Raylib.Texture;
import static com.raylib.Raylib.ToggleBorderlessWindowed;
import static com.raylib.Raylib.ToggleFullscreen;
import static com.raylib.Raylib.UnloadFont;
import static com.raylib.Raylib.UnloadImage;
import static com.raylib.Raylib.UnloadMusicStream;
import static com.raylib.Raylib.UnloadRenderTexture;
import static com.raylib.Raylib.UnloadShader;
import static com.raylib.Raylib.UnloadSound;
import static com.raylib.Raylib.UnloadSoundAlias;
import static com.raylib.Raylib.UnloadTexture;
import static com.raylib.Raylib.UnloadWave;
import static com.raylib.Raylib.UpdateCamera;
import static com.raylib.Raylib.UpdateMusicStream;
import com.raylib.Raylib.Vector2;
import com.raylib.Raylib.Vector3;
import static com.raylib.Raylib.WaitTime;
import com.raylib.Raylib.Wave;
import static com.raylib.Raylib.WaveCopy;
import static com.raylib.Raylib.WindowShouldClose;

/**
 * Mira bindings for the Raylib game-development library via Jaylib 5.5.
 *
 * <p>
 * Import this JAR in a Mira script:
 * <pre>{@code
 * import native "./raylib-RELEASE.jar" as rl;
 * rl.InitWindow(800, 600, "My Game");
 * rl.SetTargetFPS(60);
 * while (!rl.WindowShouldClose()) {
 *     rl.BeginDrawing();
 *     rl.ClearBackground(rl.BLACK);
 *     rl.DrawText("Hello!", 10, 10, 20, rl.WHITE);
 *     rl.EndDrawing();
 * }
 * rl.CloseWindow();
 * }</pre>
 *
 * <h2>Type conventions</h2>
 * <ul>
 * <li>All numbers (int, float) are passed as Mira {@code Number} (Java
 * {@code Double}).</li>
 * <li>Struct types ({@code Color}, {@code Texture}, {@code Camera3D}, etc.) are
 * opaque handles — create them with the constructor functions and pass them
 * between calls.</li>
 * <li>Functions returning {@code Vector2} / {@code Vector3} give back opaque
 * handles; use {@code Vector2X}/{@code Vector2Y} (and {@code Vector3X/Y/Z}) to
 * read components.</li>
 * <li>Shader paths accept {@code null} (or the string {@code "null"}) to use
 * the default shader for that stage.</li>
 * </ul>
 */
public class Raylib implements Lib {

    private static double toDouble(Object arg) {
        if (arg instanceof Double d) {
            return d;
        }
        return Double.parseDouble(String.valueOf(arg));
    }

    private static int toInt(Object arg) {
        return (int) toDouble(arg);
    }

    private static float toFloat(Object arg) {
        return (float) toDouble(arg);
    }

    private static Vector2 v2(Object x, Object y) {
        return new Vector2().x(toFloat(x)).y(toFloat(y));
    }

    private static Vector3 v3(Object x, Object y, Object z) {
        return new Vector3().x(toFloat(x)).y(toFloat(y)).z(toFloat(z));
    }

    private static Color clr(Object o) {
        return (Color) o;
    }

    private static Rectangle rect(Object o) {
        return (Rectangle) o;
    }

    private static String bpStr(BytePointer bp) {
        return (bp == null || bp.isNull()) ? "" : bp.getString();
    }

    private static String shaderPath(Object arg) {
        if (arg == null) {
            return null;
        }
        String s = String.valueOf(arg);
        return s.equals("null") ? null : s;
    }

    @Override
    public void loadLib(Environment environment) {
        loadConstants(environment);
        loadStructHelpers(environment);
        loadWindow(environment);
        loadCursor(environment);
        loadDraw2D(environment);
        loadDraw3D(environment);
        loadTextures(environment);
        loadImages(environment);
        loadFonts(environment);
        loadShaders(environment);
        loadBlendScissor(environment);
        loadInput(environment);
        loadGamepad(environment);
        loadTouch(environment);
        loadAudio(environment);
        loadFileIO(environment);
        loadCollision(environment);
        loadMath(environment);
        loadUtility(environment);
    }

    /**
     * Registers all Raylib constants into the environment.
     *
     * <p>
     * Includes: named colors ({@code BLACK}, {@code WHITE}, …), camera modes
     * ({@code CAMERA_FREE}, …), keyboard keys ({@code KEY_A}–{@code KEY_Z},
     * {@code KEY_F1}–{@code KEY_F12}, numpad, …), mouse buttons
     * ({@code MOUSE_BUTTON_LEFT}, …), mouse cursor shapes
     * ({@code MOUSE_CURSOR_ARROW}, …), gamepad buttons &amp; axes, config flags
     * ({@code FLAG_VSYNC_HINT}, …), log levels, blend modes, texture
     * filter/wrap modes, shader uniform types, and pixel formats.
     */
    private void loadConstants(Environment env) {
        // Color constructor
        env.define("Color", new NativeFunction(4, args
                -> new Color()
                        .r((byte) toInt(args.get(0)))
                        .g((byte) toInt(args.get(1)))
                        .b((byte) toInt(args.get(2)))
                        .a((byte) toInt(args.get(3)))));

        // Named colors
        env.define("BLACK", BLACK);
        env.define("WHITE", WHITE);
        env.define("RED", RED);
        env.define("GREEN", GREEN);
        env.define("BLUE", BLUE);
        env.define("YELLOW", YELLOW);
        env.define("ORANGE", ORANGE);
        env.define("PURPLE", PURPLE);
        env.define("PINK", PINK);
        env.define("MAROON", MAROON);
        env.define("GOLD", GOLD);
        env.define("LIME", LIME);
        env.define("SKYBLUE", SKYBLUE);
        env.define("VIOLET", VIOLET);
        env.define("DARKBLUE", DARKBLUE);
        env.define("DARKGREEN", DARKGREEN);
        env.define("DARKBROWN", DARKBROWN);
        env.define("DARKGRAY", DARKGRAY);
        env.define("GRAY", GRAY);
        env.define("LIGHTGRAY", LIGHTGRAY);
        env.define("BROWN", BROWN);
        env.define("BEIGE", BEIGE);
        env.define("MAGENTA", MAGENTA);
        env.define("DARKPURPLE", DARKPURPLE);
        env.define("RAYWHITE", RAYWHITE);

        // Camera mode
        env.define("CAMERA_CUSTOM", 0.0);
        env.define("CAMERA_FREE", 1.0);
        env.define("CAMERA_ORBITAL", 2.0);
        env.define("CAMERA_FIRST_PERSON", 3.0);
        env.define("CAMERA_THIRD_PERSON", 4.0);
        env.define("CAMERA_PERSPECTIVE", 0.0);
        env.define("CAMERA_ORTHOGRAPHIC", 1.0);

        // Keyboard keys (existing)
        env.define("KEY_SPACE", 32.0);
        env.define("KEY_ENTER", 257.0);
        env.define("KEY_ESCAPE", 256.0);
        env.define("KEY_BACKSPACE", 259.0);
        env.define("KEY_TAB", 258.0);
        env.define("KEY_DELETE", 261.0);
        env.define("KEY_UP", 265.0);
        env.define("KEY_DOWN", 264.0);
        env.define("KEY_LEFT", 263.0);
        env.define("KEY_RIGHT", 262.0);
        env.define("KEY_LEFT_SHIFT", 340.0);
        env.define("KEY_LEFT_CTRL", 341.0);
        env.define("KEY_LEFT_ALT", 342.0);
        env.define("KEY_A", 65.0);
        env.define("KEY_B", 66.0);
        env.define("KEY_C", 67.0);
        env.define("KEY_D", 68.0);
        env.define("KEY_E", 69.0);
        env.define("KEY_F", 70.0);
        env.define("KEY_G", 71.0);
        env.define("KEY_H", 72.0);
        env.define("KEY_I", 73.0);
        env.define("KEY_J", 74.0);
        env.define("KEY_K", 75.0);
        env.define("KEY_L", 76.0);
        env.define("KEY_M", 77.0);
        env.define("KEY_N", 78.0);
        env.define("KEY_O", 79.0);
        env.define("KEY_P", 80.0);
        env.define("KEY_Q", 81.0);
        env.define("KEY_R", 82.0);
        env.define("KEY_S", 83.0);
        env.define("KEY_T", 84.0);
        env.define("KEY_U", 85.0);
        env.define("KEY_V", 86.0);
        env.define("KEY_W", 87.0);
        env.define("KEY_X", 88.0);
        env.define("KEY_Y", 89.0);
        env.define("KEY_Z", 90.0);
        env.define("KEY_0", 48.0);
        env.define("KEY_1", 49.0);
        env.define("KEY_2", 50.0);
        env.define("KEY_3", 51.0);
        env.define("KEY_4", 52.0);
        env.define("KEY_5", 53.0);
        env.define("KEY_6", 54.0);
        env.define("KEY_7", 55.0);
        env.define("KEY_8", 56.0);
        env.define("KEY_9", 57.0);

        // Keyboard keys (additional)
        env.define("KEY_NULL", 0.0);
        env.define("KEY_APOSTROPHE", 39.0);
        env.define("KEY_COMMA", 44.0);
        env.define("KEY_MINUS", 45.0);
        env.define("KEY_PERIOD", 46.0);
        env.define("KEY_SLASH", 47.0);
        env.define("KEY_SEMICOLON", 59.0);
        env.define("KEY_EQUAL", 61.0);
        env.define("KEY_LEFT_BRACKET", 91.0);
        env.define("KEY_BACKSLASH", 92.0);
        env.define("KEY_RIGHT_BRACKET", 93.0);
        env.define("KEY_GRAVE", 96.0);
        env.define("KEY_INSERT", 260.0);
        env.define("KEY_PAGE_UP", 266.0);
        env.define("KEY_PAGE_DOWN", 267.0);
        env.define("KEY_HOME", 268.0);
        env.define("KEY_END", 269.0);
        env.define("KEY_CAPS_LOCK", 280.0);
        env.define("KEY_SCROLL_LOCK", 281.0);
        env.define("KEY_NUM_LOCK", 282.0);
        env.define("KEY_PRINT_SCREEN", 283.0);
        env.define("KEY_PAUSE", 284.0);
        env.define("KEY_F1", 290.0);
        env.define("KEY_F2", 291.0);
        env.define("KEY_F3", 292.0);
        env.define("KEY_F4", 293.0);
        env.define("KEY_F5", 294.0);
        env.define("KEY_F6", 295.0);
        env.define("KEY_F7", 296.0);
        env.define("KEY_F8", 297.0);
        env.define("KEY_F9", 298.0);
        env.define("KEY_F10", 299.0);
        env.define("KEY_F11", 300.0);
        env.define("KEY_F12", 301.0);
        env.define("KEY_KP_0", 320.0);
        env.define("KEY_KP_1", 321.0);
        env.define("KEY_KP_2", 322.0);
        env.define("KEY_KP_3", 323.0);
        env.define("KEY_KP_4", 324.0);
        env.define("KEY_KP_5", 325.0);
        env.define("KEY_KP_6", 326.0);
        env.define("KEY_KP_7", 327.0);
        env.define("KEY_KP_8", 328.0);
        env.define("KEY_KP_9", 329.0);
        env.define("KEY_KP_DECIMAL", 330.0);
        env.define("KEY_KP_DIVIDE", 331.0);
        env.define("KEY_KP_MULTIPLY", 332.0);
        env.define("KEY_KP_SUBTRACT", 333.0);
        env.define("KEY_KP_ADD", 334.0);
        env.define("KEY_KP_ENTER", 335.0);
        env.define("KEY_KP_EQUAL", 336.0);
        env.define("KEY_RIGHT_SHIFT", 344.0);
        env.define("KEY_RIGHT_CTRL", 345.0);
        env.define("KEY_RIGHT_ALT", 346.0);
        env.define("KEY_RIGHT_SUPER", 347.0);
        env.define("KEY_KB_MENU", 348.0);

        // Mouse buttons (existing aliases)
        env.define("MOUSE_LEFT", 0.0);
        env.define("MOUSE_RIGHT", 1.0);
        env.define("MOUSE_MIDDLE", 2.0);
        // Mouse buttons (canonical names)
        env.define("MOUSE_BUTTON_LEFT", 0.0);
        env.define("MOUSE_BUTTON_RIGHT", 1.0);
        env.define("MOUSE_BUTTON_MIDDLE", 2.0);
        env.define("MOUSE_BUTTON_SIDE", 3.0);
        env.define("MOUSE_BUTTON_EXTRA", 4.0);
        env.define("MOUSE_BUTTON_FORWARD", 5.0);
        env.define("MOUSE_BUTTON_BACK", 6.0);

        // Mouse cursor
        env.define("MOUSE_CURSOR_DEFAULT", 0.0);
        env.define("MOUSE_CURSOR_ARROW", 1.0);
        env.define("MOUSE_CURSOR_IBEAM", 2.0);
        env.define("MOUSE_CURSOR_CROSSHAIR", 3.0);
        env.define("MOUSE_CURSOR_POINTING_HAND", 4.0);
        env.define("MOUSE_CURSOR_RESIZE_EW", 5.0);
        env.define("MOUSE_CURSOR_RESIZE_NS", 6.0);
        env.define("MOUSE_CURSOR_RESIZE_NWSE", 7.0);
        env.define("MOUSE_CURSOR_RESIZE_NESW", 8.0);
        env.define("MOUSE_CURSOR_RESIZE_ALL", 9.0);
        env.define("MOUSE_CURSOR_NOT_ALLOWED", 10.0);

        // Gamepad buttons
        env.define("GAMEPAD_BUTTON_UNKNOWN", 0.0);
        env.define("GAMEPAD_BUTTON_LEFT_FACE_UP", 1.0);
        env.define("GAMEPAD_BUTTON_LEFT_FACE_RIGHT", 2.0);
        env.define("GAMEPAD_BUTTON_LEFT_FACE_DOWN", 3.0);
        env.define("GAMEPAD_BUTTON_LEFT_FACE_LEFT", 4.0);
        env.define("GAMEPAD_BUTTON_RIGHT_FACE_UP", 5.0);
        env.define("GAMEPAD_BUTTON_RIGHT_FACE_RIGHT", 6.0);
        env.define("GAMEPAD_BUTTON_RIGHT_FACE_DOWN", 7.0);
        env.define("GAMEPAD_BUTTON_RIGHT_FACE_LEFT", 8.0);
        env.define("GAMEPAD_BUTTON_LEFT_TRIGGER_1", 9.0);
        env.define("GAMEPAD_BUTTON_LEFT_TRIGGER_2", 10.0);
        env.define("GAMEPAD_BUTTON_RIGHT_TRIGGER_1", 11.0);
        env.define("GAMEPAD_BUTTON_RIGHT_TRIGGER_2", 12.0);
        env.define("GAMEPAD_BUTTON_MIDDLE_LEFT", 13.0);
        env.define("GAMEPAD_BUTTON_MIDDLE", 14.0);
        env.define("GAMEPAD_BUTTON_MIDDLE_RIGHT", 15.0);
        env.define("GAMEPAD_BUTTON_LEFT_THUMB", 16.0);
        env.define("GAMEPAD_BUTTON_RIGHT_THUMB", 17.0);

        // Gamepad axes
        env.define("GAMEPAD_AXIS_LEFT_X", 0.0);
        env.define("GAMEPAD_AXIS_LEFT_Y", 1.0);
        env.define("GAMEPAD_AXIS_RIGHT_X", 2.0);
        env.define("GAMEPAD_AXIS_RIGHT_Y", 3.0);
        env.define("GAMEPAD_AXIS_LEFT_TRIGGER", 4.0);
        env.define("GAMEPAD_AXIS_RIGHT_TRIGGER", 5.0);

        // Config flags
        env.define("FLAG_VSYNC_HINT", 64.0);
        env.define("FLAG_FULLSCREEN_MODE", 2.0);
        env.define("FLAG_WINDOW_RESIZABLE", 4.0);
        env.define("FLAG_WINDOW_UNDECORATED", 8.0);
        env.define("FLAG_WINDOW_HIDDEN", 128.0);
        env.define("FLAG_WINDOW_MINIMIZED", 512.0);
        env.define("FLAG_WINDOW_MAXIMIZED", 1024.0);
        env.define("FLAG_WINDOW_UNFOCUSED", 2048.0);
        env.define("FLAG_WINDOW_TOPMOST", 4096.0);
        env.define("FLAG_WINDOW_ALWAYS_RUN", 256.0);
        env.define("FLAG_WINDOW_TRANSPARENT", 16.0);
        env.define("FLAG_WINDOW_HIGHDPI", 8192.0);
        env.define("FLAG_WINDOW_MOUSE_PASSTHROUGH", 16384.0);
        env.define("FLAG_BORDERLESS_WINDOWED_MODE", 32768.0);
        env.define("FLAG_MSAA_4X_HINT", 32.0);
        env.define("FLAG_INTERLACED_HINT", 65536.0);

        // Log levels
        env.define("LOG_ALL", 0.0);
        env.define("LOG_TRACE", 1.0);
        env.define("LOG_DEBUG", 2.0);
        env.define("LOG_INFO", 3.0);
        env.define("LOG_WARNING", 4.0);
        env.define("LOG_ERROR", 5.0);
        env.define("LOG_FATAL", 6.0);
        env.define("LOG_NONE", 7.0);

        // Blend modes
        env.define("BLEND_ALPHA", 0.0);
        env.define("BLEND_ADDITIVE", 1.0);
        env.define("BLEND_MULTIPLIED", 2.0);
        env.define("BLEND_ADD_COLORS", 3.0);
        env.define("BLEND_SUBTRACT_COLORS", 4.0);
        env.define("BLEND_ALPHA_PREMULTIPLY", 5.0);
        env.define("BLEND_CUSTOM", 6.0);
        env.define("BLEND_CUSTOM_SEPARATE", 7.0);

        // Texture filter
        env.define("TEXTURE_FILTER_POINT", 0.0);
        env.define("TEXTURE_FILTER_BILINEAR", 1.0);
        env.define("TEXTURE_FILTER_TRILINEAR", 2.0);
        env.define("TEXTURE_FILTER_ANISOTROPIC_4X", 3.0);
        env.define("TEXTURE_FILTER_ANISOTROPIC_8X", 4.0);
        env.define("TEXTURE_FILTER_ANISOTROPIC_16X", 5.0);

        // Texture wrap
        env.define("TEXTURE_WRAP_REPEAT", 0.0);
        env.define("TEXTURE_WRAP_CLAMP", 1.0);
        env.define("TEXTURE_WRAP_MIRROR_REPEAT", 2.0);
        env.define("TEXTURE_WRAP_MIRROR_CLAMP", 3.0);

        // Shader uniform types
        env.define("SHADER_UNIFORM_FLOAT", 0.0);
        env.define("SHADER_UNIFORM_VEC2", 1.0);
        env.define("SHADER_UNIFORM_VEC3", 2.0);
        env.define("SHADER_UNIFORM_VEC4", 3.0);
        env.define("SHADER_UNIFORM_INT", 4.0);
        env.define("SHADER_UNIFORM_IVEC2", 5.0);
        env.define("SHADER_UNIFORM_IVEC3", 6.0);
        env.define("SHADER_UNIFORM_IVEC4", 7.0);
        env.define("SHADER_UNIFORM_SAMPLER2D", 8.0);

        // Pixel formats (commonly used)
        env.define("PIXELFORMAT_UNCOMPRESSED_GRAYSCALE", 1.0);
        env.define("PIXELFORMAT_UNCOMPRESSED_GRAY_ALPHA", 2.0);
        env.define("PIXELFORMAT_UNCOMPRESSED_R5G6B5", 3.0);
        env.define("PIXELFORMAT_UNCOMPRESSED_R8G8B8", 4.0);
        env.define("PIXELFORMAT_UNCOMPRESSED_R5G5B5A1", 5.0);
        env.define("PIXELFORMAT_UNCOMPRESSED_R4G4B4A4", 6.0);
        env.define("PIXELFORMAT_UNCOMPRESSED_R8G8B8A8", 7.0);
        env.define("PIXELFORMAT_UNCOMPRESSED_R32", 8.0);
        env.define("PIXELFORMAT_UNCOMPRESSED_R32G32B32", 9.0);
        env.define("PIXELFORMAT_UNCOMPRESSED_R32G32B32A32", 10.0);
    }

    /**
     * Registers struct constructors and field accessors.
     *
     * <p>
     * <b>Constructors</b> — return opaque handles used as arguments to other
     * functions:
     * <ul>
     * <li>{@code Vector2(x, y) -> Vector2}</li>
     * <li>{@code Vector3(x, y, z) -> Vector3}</li>
     * <li>{@code Rectangle(x, y, width, height) -> Rectangle}</li>
     * <li>{@code Ray(posX, posY, posZ, dirX, dirY, dirZ) -> Ray}</li>
     * <li>{@code BoundingBox(minX, minY, minZ, maxX, maxY, maxZ) -> BoundingBox}</li>
     * </ul>
     *
     * <p>
     * <b>Field accessors</b>:
     * <ul>
     * <li>{@code Vector2X(v) -> Number}, {@code Vector2Y(v) -> Number}</li>
     * <li>{@code Vector3X(v) -> Number}, {@code Vector3Y(v) -> Number},
     * {@code Vector3Z(v) -> Number}</li>
     * <li>{@code RectX(r) -> Number}, {@code RectY(r) -> Number},
     * {@code RectWidth(r) -> Number}, {@code RectHeight(r) -> Number}</li>
     * <li>{@code RayCollisionHit(rc) -> Boolean},
     * {@code RayCollisionDistance(rc) -> Number},
     * {@code RayCollisionPoint(rc) -> Vector3}</li>
     * <li>{@code ImageWidth(img) -> Number},
     * {@code ImageHeight(img) -> Number}</li>
     * <li>{@code TextureWidth(tex) -> Number},
     * {@code TextureHeight(tex) -> Number}</li>
     * <li>{@code GetRenderTextureTexture(rt) -> Texture}</li>
     * <li>{@code FontBaseSize(font) -> Number}</li>
     * </ul>
     */
    private void loadStructHelpers(Environment env) {
        // Vector2 constructor and accessors
        env.define("Vector2", new NativeFunction(2, args
                -> v2(args.get(0), args.get(1))));
        env.define("Vector2X", new NativeFunction(1, args
                -> (double) ((Vector2) args.get(0)).x()));
        env.define("Vector2Y", new NativeFunction(1, args
                -> (double) ((Vector2) args.get(0)).y()));

        // Vector3 constructor and accessors
        env.define("Vector3", new NativeFunction(3, args
                -> v3(args.get(0), args.get(1), args.get(2))));
        env.define("Vector3X", new NativeFunction(1, args
                -> (double) ((Vector3) args.get(0)).x()));
        env.define("Vector3Y", new NativeFunction(1, args
                -> (double) ((Vector3) args.get(0)).y()));
        env.define("Vector3Z", new NativeFunction(1, args
                -> (double) ((Vector3) args.get(0)).z()));

        // Rectangle constructor and accessors
        env.define("Rectangle", new NativeFunction(4, args
                -> new Rectangle()
                        .x(toFloat(args.get(0))).y(toFloat(args.get(1)))
                        .width(toFloat(args.get(2))).height(toFloat(args.get(3)))));
        env.define("RectX", new NativeFunction(1, args
                -> (double) ((Rectangle) args.get(0)).x()));
        env.define("RectY", new NativeFunction(1, args
                -> (double) ((Rectangle) args.get(0)).y()));
        env.define("RectWidth", new NativeFunction(1, args
                -> (double) ((Rectangle) args.get(0)).width()));
        env.define("RectHeight", new NativeFunction(1, args
                -> (double) ((Rectangle) args.get(0)).height()));

        // Ray constructor
        env.define("Ray", new NativeFunction(6, args
                -> new Ray()
                        ._position(v3(args.get(0), args.get(1), args.get(2)))
                        .direction(v3(args.get(3), args.get(4), args.get(5)))));

        // BoundingBox constructor
        env.define("BoundingBox", new NativeFunction(6, args
                -> new BoundingBox()
                        .min(v3(args.get(0), args.get(1), args.get(2)))
                        .max(v3(args.get(3), args.get(4), args.get(5)))));

        // RayCollision accessors
        env.define("RayCollisionHit", new NativeFunction(1, args
                -> ((RayCollision) args.get(0)).hit()));
        env.define("RayCollisionDistance", new NativeFunction(1, args
                -> (double) ((RayCollision) args.get(0)).distance()));
        env.define("RayCollisionPoint", new NativeFunction(1, args
                -> ((RayCollision) args.get(0)).point()));

        // Image accessors
        env.define("ImageWidth", new NativeFunction(1, args
                -> (double) ((Image) args.get(0)).width()));
        env.define("ImageHeight", new NativeFunction(1, args
                -> (double) ((Image) args.get(0)).height()));

        // Texture accessors
        env.define("TextureWidth", new NativeFunction(1, args
                -> (double) ((Texture) args.get(0)).width()));
        env.define("TextureHeight", new NativeFunction(1, args
                -> (double) ((Texture) args.get(0)).height()));

        // RenderTexture accessor
        env.define("GetRenderTextureTexture", new NativeFunction(1, args
                -> ((RenderTexture) args.get(0)).texture()));

        // Font accessor
        env.define("FontBaseSize", new NativeFunction(1, args
                -> (double) ((Font) args.get(0)).baseSize()));
    }

    /**
     * Registers window-management, frame-drawing, timing, and camera functions.
     *
     * <p>
     * <b>Window lifecycle</b>: {@code InitWindow(w, h, title)}, {@code CloseWindow()},
     * {@code WindowShouldClose() -> Boolean},
     * {@code IsWindowReady/Hidden/Minimized/Maximized/Focused/Resized/Fullscreen() -> Boolean}
     *
     * <p>
     * <b>Window settings</b>: {@code SetWindowTitle(title)}, {@code SetWindowSize(w, h)},
     * {@code SetWindowPosition(x, y)}, {@code SetWindowMinSize/MaxSize(w, h)},
     * {@code SetWindowOpacity(alpha)}, {@code SetWindowFocused()},
     * {@code ToggleFullscreen()}, {@code ToggleBorderlessWindowed()},
     * {@code MinimizeWindow()}, {@code MaximizeWindow()}, {@code RestoreWindow()}
     *
     * <p>
     * <b>Monitor info</b>: {@code GetMonitorCount() -> Number},
     * {@code GetCurrentMonitor() -> Number},
     * {@code GetMonitorWidth/Height/RefreshRate(monitor) -> Number},
     * {@code GetMonitorName(monitor) -> String},
     * {@code GetRenderWidth/Height() -> Number},
     * {@code GetScreenWidth/Height() -> Number}
     *
     * <p>
     * <b>Frame</b>: {@code BeginDrawing()}, {@code EndDrawing()}, {@code ClearBackground(color)},
     * {@code SetTargetFPS(fps)}, {@code GetTime() -> Number},
     * {@code GetFrameTime() -> Number}
     *
     * <p>
     * <b>Misc</b>:
     * {@code SetConfigFlags(flags)}, {@code TakeScreenshot(filename)}
     *
     * <p>
     * <b>Camera 3D</b>:
     * {@code Camera3D(posX, posY, posZ, targetX, targetY, targetZ, fovy) -> Camera3D},
     * {@code BeginMode3D(camera)}, {@code EndMode3D()}, {@code UpdateCamera(camera, mode)}
     *
     * <p>
     * <b>Camera 2D</b>:
     * {@code Camera2D(offsetX, offsetY, targetX, targetY, rotation, zoom) -> Camera2D},
     * {@code BeginMode2D(camera)}, {@code EndMode2D()}
     */
    private void loadWindow(Environment env) {
        env.define("InitWindow", new NativeFunction(3, args -> {
            InitWindow(toInt(args.get(0)), toInt(args.get(1)), String.valueOf(args.get(2)));
            return null;
        }));
        env.define("CloseWindow", new NativeFunction(0, args -> {
            CloseWindow();
            return null;
        }));
        env.define("WindowShouldClose", new NativeFunction(0, args -> WindowShouldClose()));
        env.define("IsWindowReady", new NativeFunction(0, args -> IsWindowReady()));
        env.define("IsWindowHidden", new NativeFunction(0, args -> IsWindowHidden()));
        env.define("IsWindowMinimized", new NativeFunction(0, args -> IsWindowMinimized()));
        env.define("IsWindowMaximized", new NativeFunction(0, args -> IsWindowMaximized()));
        env.define("IsWindowFocused", new NativeFunction(0, args -> IsWindowFocused()));
        env.define("IsWindowResized", new NativeFunction(0, args -> IsWindowResized()));
        env.define("IsWindowFullscreen", new NativeFunction(0, args -> IsWindowFullscreen()));

        env.define("SetTargetFPS", new NativeFunction(1, args -> {
            SetTargetFPS(toInt(args.get(0)));
            return null;
        }));
        env.define("GetScreenWidth", new NativeFunction(0, args -> (double) GetScreenWidth()));
        env.define("GetScreenHeight", new NativeFunction(0, args -> (double) GetScreenHeight()));
        env.define("GetRenderWidth", new NativeFunction(0, args -> (double) GetRenderWidth()));
        env.define("GetRenderHeight", new NativeFunction(0, args -> (double) GetRenderHeight()));

        env.define("SetWindowTitle", new NativeFunction(1, args -> {
            SetWindowTitle(String.valueOf(args.get(0)));
            return null;
        }));
        env.define("SetWindowSize", new NativeFunction(2, args -> {
            SetWindowSize(toInt(args.get(0)), toInt(args.get(1)));
            return null;
        }));
        env.define("SetWindowPosition", new NativeFunction(2, args -> {
            SetWindowPosition(toInt(args.get(0)), toInt(args.get(1)));
            return null;
        }));
        env.define("SetWindowMinSize", new NativeFunction(2, args -> {
            SetWindowMinSize(toInt(args.get(0)), toInt(args.get(1)));
            return null;
        }));
        env.define("SetWindowMaxSize", new NativeFunction(2, args -> {
            SetWindowMaxSize(toInt(args.get(0)), toInt(args.get(1)));
            return null;
        }));
        env.define("SetWindowOpacity", new NativeFunction(1, args -> {
            SetWindowOpacity(toFloat(args.get(0)));
            return null;
        }));
        env.define("SetWindowFocused", new NativeFunction(0, args -> {
            SetWindowFocused();
            return null;
        }));

        env.define("ToggleFullscreen", new NativeFunction(0, args -> {
            ToggleFullscreen();
            return null;
        }));
        env.define("ToggleBorderlessWindowed", new NativeFunction(0, args -> {
            ToggleBorderlessWindowed();
            return null;
        }));
        env.define("MinimizeWindow", new NativeFunction(0, args -> {
            MinimizeWindow();
            return null;
        }));
        env.define("MaximizeWindow", new NativeFunction(0, args -> {
            MaximizeWindow();
            return null;
        }));
        env.define("RestoreWindow", new NativeFunction(0, args -> {
            RestoreWindow();
            return null;
        }));

        env.define("GetMonitorCount", new NativeFunction(0, args -> (double) GetMonitorCount()));
        env.define("GetCurrentMonitor", new NativeFunction(0, args -> (double) GetCurrentMonitor()));
        env.define("GetMonitorWidth", new NativeFunction(1, args
                -> (double) GetMonitorWidth(toInt(args.get(0)))));
        env.define("GetMonitorHeight", new NativeFunction(1, args
                -> (double) GetMonitorHeight(toInt(args.get(0)))));
        env.define("GetMonitorRefreshRate", new NativeFunction(1, args
                -> (double) GetMonitorRefreshRate(toInt(args.get(0)))));
        env.define("GetMonitorName", new NativeFunction(1, args
                -> bpStr(GetMonitorName(toInt(args.get(0))))));

        env.define("SetConfigFlags", new NativeFunction(1, args -> {
            SetConfigFlags(toInt(args.get(0)));
            return null;
        }));
        env.define("TakeScreenshot", new NativeFunction(1, args -> {
            TakeScreenshot(String.valueOf(args.get(0)));
            return null;
        }));

        // Drawing frame
        env.define("BeginDrawing", new NativeFunction(0, args -> {
            BeginDrawing();
            return null;
        }));
        env.define("EndDrawing", new NativeFunction(0, args -> {
            EndDrawing();
            return null;
        }));
        env.define("ClearBackground", new NativeFunction(1, args -> {
            ClearBackground(clr(args.get(0)));
            return null;
        }));

        // Timing
        env.define("GetTime", new NativeFunction(0, args -> GetTime()));
        env.define("GetFrameTime", new NativeFunction(0, args -> (double) GetFrameTime()));

        // Camera3D
        env.define("Camera3D", new NativeFunction(7, args
                -> new Camera3D()
                        ._position(v3(args.get(0), args.get(1), args.get(2)))
                        .target(v3(args.get(3), args.get(4), args.get(5)))
                        .up(new Vector3().x(0).y(1).z(0))
                        .fovy(toFloat(args.get(6)))
                        .projection(CAMERA_PERSPECTIVE)));
        env.define("BeginMode3D", new NativeFunction(1, args -> {
            BeginMode3D((Camera3D) args.get(0));
            return null;
        }));
        env.define("EndMode3D", new NativeFunction(0, args -> {
            EndMode3D();
            return null;
        }));
        env.define("UpdateCamera", new NativeFunction(2, args -> {
            UpdateCamera((Camera3D) args.get(0), toInt(args.get(1)));
            return null;
        }));

        // Camera2D
        env.define("Camera2D", new NativeFunction(6, args
                -> new Camera2D()
                        .offset(v2(args.get(0), args.get(1)))
                        .target(v2(args.get(2), args.get(3)))
                        .rotation(toFloat(args.get(4)))
                        .zoom(toFloat(args.get(5)))));
        env.define("BeginMode2D", new NativeFunction(1, args -> {
            BeginMode2D((Camera2D) args.get(0));
            return null;
        }));
        env.define("EndMode2D", new NativeFunction(0, args -> {
            EndMode2D();
            return null;
        }));
    }

    /**
     * Registers mouse-cursor visibility and lock functions.
     *
     * <ul>
     * <li>{@code ShowCursor()}, {@code HideCursor()}, {@code IsCursorHidden() -> Boolean}</li>
     * <li>{@code EnableCursor()}, {@code DisableCursor()}, {@code IsCursorOnScreen() -> Boolean}</li>
     * </ul>
     */
    private void loadCursor(Environment env) {
        env.define("ShowCursor", new NativeFunction(0, args -> {
            ShowCursor();
            return null;
        }));
        env.define("HideCursor", new NativeFunction(0, args -> {
            HideCursor();
            return null;
        }));
        env.define("IsCursorHidden", new NativeFunction(0, args -> IsCursorHidden()));
        env.define("EnableCursor", new NativeFunction(0, args -> {
            EnableCursor();
            return null;
        }));
        env.define("DisableCursor", new NativeFunction(0, args -> {
            DisableCursor();
            return null;
        }));
        env.define("IsCursorOnScreen", new NativeFunction(0, args -> IsCursorOnScreen()));
    }

    /**
     * Registers all 2D drawing primitives.
     *
     * <p>
     * <b>Pixels &amp; lines</b>:      {@code DrawPixel(x, y, color)}, {@code DrawPixelV(x, y, color)},
     * {@code DrawLine(x1, y1, x2, y2, color)},
     * {@code DrawLineEx(x1, y1, x2, y2, thick, color)},
     * {@code DrawLineBezier(x1, y1, x2, y2, thick, color)}
     *
     * <p>
     * <b>Circles</b>:      {@code DrawCircle(cx, cy, r, color)}, {@code DrawCircleV(cx, cy, r, color)},
     * {@code DrawCircleGradient(cx, cy, r, innerColor, outerColor)},
     * {@code DrawCircleLines(cx, cy, r, color)}, {@code DrawCircleLinesV(cx, cy, r, color)},
     * {@code DrawCircleSector(cx, cy, r, startAngle, endAngle, segments, color)},
     * {@code DrawCircleSectorLines(cx, cy, r, startAngle, endAngle, segments, color)}
     *
     * <p>
     * <b>Ellipse &amp; ring</b>:      {@code DrawEllipse(cx, cy, rx, ry, color)}, {@code DrawEllipseLines(cx, cy, rx, ry, color)},
     * {@code DrawRing(cx, cy, innerR, outerR, startAngle, endAngle, segments, color)},
     * {@code DrawRingLines(cx, cy, innerR, outerR, startAngle, endAngle, segments, color)}
     *
     * <p>
     * <b>Rectangles</b>:      {@code DrawRectangle(x, y, w, h, color)}, {@code DrawRectangleV(px, py, sw, sh, color)},
     * {@code DrawRectangleRec(rect, color)},
     * {@code DrawRectanglePro(rect, originX, originY, rotation, color)},
     * {@code DrawRectangleGradientH/V(x, y, w, h, color1, color2)},
     * {@code DrawRectangleGradientEx(rect, c1, c2, c3, c4)},
     * {@code DrawRectangleLines(x, y, w, h, color)},
     * {@code DrawRectangleLinesEx(rect, thick, color)},
     * {@code DrawRectangleRounded(rect, roundness, segments, color)},
     * {@code DrawRectangleRoundedLines(rect, roundness, segments, color)},
     * {@code DrawRectangleRoundedLinesEx(rect, roundness, segments, thick, color)}
     *
     * <p>
     * <b>Triangles &amp; polygons</b>:      {@code DrawTriangle(x1, y1, x2, y2, x3, y3, color)},
     * {@code DrawTriangleLines(x1, y1, x2, y2, x3, y3, color)},
     * {@code DrawPoly(cx, cy, sides, radius, rotation, color)},
     * {@code DrawPolyLines(cx, cy, sides, radius, rotation, color)},
     * {@code DrawPolyLinesEx(cx, cy, sides, radius, rotation, thick, color)}
     *
     * <p>
     * <b>Text</b>:      {@code DrawText(text, x, y, fontSize, color)}, {@code DrawFPS(x, y)},
     * {@code MeasureText(text, fontSize) -> Number}
     */
    private void loadDraw2D(Environment env) {
        // Pixel
        env.define("DrawPixel", new NativeFunction(3, args -> {
            DrawPixel(toInt(args.get(0)), toInt(args.get(1)), clr(args.get(2)));
            return null;
        }));
        env.define("DrawPixelV", new NativeFunction(3, args -> {
            DrawPixelV(v2(args.get(0), args.get(1)), clr(args.get(2)));
            return null;
        }));

        // Lines
        env.define("DrawLine", new NativeFunction(5, args -> {
            DrawLine(toInt(args.get(0)), toInt(args.get(1)),
                    toInt(args.get(2)), toInt(args.get(3)), clr(args.get(4)));
            return null;
        }));
        env.define("DrawLineEx", new NativeFunction(6, args -> {
            DrawLineEx(v2(args.get(0), args.get(1)), v2(args.get(2), args.get(3)),
                    toFloat(args.get(4)), clr(args.get(5)));
            return null;
        }));
        env.define("DrawLineBezier", new NativeFunction(6, args -> {
            DrawLineBezier(v2(args.get(0), args.get(1)), v2(args.get(2), args.get(3)),
                    toFloat(args.get(4)), clr(args.get(5)));
            return null;
        }));

        // Circles
        env.define("DrawCircle", new NativeFunction(4, args -> {
            DrawCircle(toInt(args.get(0)), toInt(args.get(1)),
                    toFloat(args.get(2)), clr(args.get(3)));
            return null;
        }));
        env.define("DrawCircleV", new NativeFunction(4, args -> {
            DrawCircleV(v2(args.get(0), args.get(1)), toFloat(args.get(2)),
                    clr(args.get(3)));
            return null;
        }));
        env.define("DrawCircleGradient", new NativeFunction(5, args -> {
            DrawCircleGradient(toInt(args.get(0)), toInt(args.get(1)), toFloat(args.get(2)),
                    clr(args.get(3)), clr(args.get(4)));
            return null;
        }));
        env.define("DrawCircleLines", new NativeFunction(4, args -> {
            DrawCircleLines(toInt(args.get(0)), toInt(args.get(1)),
                    toFloat(args.get(2)), clr(args.get(3)));
            return null;
        }));
        env.define("DrawCircleLinesV", new NativeFunction(4, args -> {
            DrawCircleLinesV(v2(args.get(0), args.get(1)), toFloat(args.get(2)),
                    clr(args.get(3)));
            return null;
        }));
        env.define("DrawCircleSector", new NativeFunction(7, args -> {
            DrawCircleSector(v2(args.get(0), args.get(1)), toFloat(args.get(2)),
                    toFloat(args.get(3)), toFloat(args.get(4)),
                    toInt(args.get(5)), clr(args.get(6)));
            return null;
        }));
        env.define("DrawCircleSectorLines", new NativeFunction(7, args -> {
            DrawCircleSectorLines(v2(args.get(0), args.get(1)), toFloat(args.get(2)),
                    toFloat(args.get(3)), toFloat(args.get(4)),
                    toInt(args.get(5)), clr(args.get(6)));
            return null;
        }));

        // Ellipse
        env.define("DrawEllipse", new NativeFunction(5, args -> {
            DrawEllipse(toInt(args.get(0)), toInt(args.get(1)),
                    toFloat(args.get(2)), toFloat(args.get(3)), clr(args.get(4)));
            return null;
        }));
        env.define("DrawEllipseLines", new NativeFunction(5, args -> {
            DrawEllipseLines(toInt(args.get(0)), toInt(args.get(1)),
                    toFloat(args.get(2)), toFloat(args.get(3)), clr(args.get(4)));
            return null;
        }));

        // Ring
        env.define("DrawRing", new NativeFunction(8, args -> {
            DrawRing(v2(args.get(0), args.get(1)), toFloat(args.get(2)), toFloat(args.get(3)),
                    toFloat(args.get(4)), toFloat(args.get(5)),
                    toInt(args.get(6)), clr(args.get(7)));
            return null;
        }));
        env.define("DrawRingLines", new NativeFunction(8, args -> {
            DrawRingLines(v2(args.get(0), args.get(1)), toFloat(args.get(2)), toFloat(args.get(3)),
                    toFloat(args.get(4)), toFloat(args.get(5)),
                    toInt(args.get(6)), clr(args.get(7)));
            return null;
        }));

        // Rectangles
        env.define("DrawRectangle", new NativeFunction(5, args -> {
            DrawRectangle(toInt(args.get(0)), toInt(args.get(1)),
                    toInt(args.get(2)), toInt(args.get(3)), clr(args.get(4)));
            return null;
        }));
        env.define("DrawRectangleV", new NativeFunction(5, args -> {
            DrawRectangleV(v2(args.get(0), args.get(1)),
                    v2(args.get(2), args.get(3)), clr(args.get(4)));
            return null;
        }));
        env.define("DrawRectangleRec", new NativeFunction(2, args -> {
            DrawRectangleRec(rect(args.get(0)), clr(args.get(1)));
            return null;
        }));
        env.define("DrawRectanglePro", new NativeFunction(5, args -> {
            DrawRectanglePro(rect(args.get(0)), v2(args.get(1), args.get(2)),
                    toFloat(args.get(3)), clr(args.get(4)));
            return null;
        }));
        env.define("DrawRectangleGradientH", new NativeFunction(6, args -> {
            DrawRectangleGradientH(toInt(args.get(0)), toInt(args.get(1)),
                    toInt(args.get(2)), toInt(args.get(3)),
                    clr(args.get(4)), clr(args.get(5)));
            return null;
        }));
        env.define("DrawRectangleGradientV", new NativeFunction(6, args -> {
            DrawRectangleGradientV(toInt(args.get(0)), toInt(args.get(1)),
                    toInt(args.get(2)), toInt(args.get(3)),
                    clr(args.get(4)), clr(args.get(5)));
            return null;
        }));
        env.define("DrawRectangleGradientEx", new NativeFunction(5, args -> {
            DrawRectangleGradientEx(rect(args.get(0)), clr(args.get(1)),
                    clr(args.get(2)), clr(args.get(3)),
                    clr(args.get(4)));
            return null;
        }));
        env.define("DrawRectangleLines", new NativeFunction(5, args -> {
            DrawRectangleLines(toInt(args.get(0)), toInt(args.get(1)),
                    toInt(args.get(2)), toInt(args.get(3)), clr(args.get(4)));
            return null;
        }));
        env.define("DrawRectangleLinesEx", new NativeFunction(3, args -> {
            DrawRectangleLinesEx(rect(args.get(0)), toFloat(args.get(1)),
                    clr(args.get(2)));
            return null;
        }));
        env.define("DrawRectangleRounded", new NativeFunction(4, args -> {
            DrawRectangleRounded(rect(args.get(0)), toFloat(args.get(1)),
                    toInt(args.get(2)), clr(args.get(3)));
            return null;
        }));
        env.define("DrawRectangleRoundedLines", new NativeFunction(4, args -> {
            DrawRectangleRoundedLines(rect(args.get(0)), toFloat(args.get(1)),
                    toInt(args.get(2)), clr(args.get(3)));
            return null;
        }));
        env.define("DrawRectangleRoundedLinesEx", new NativeFunction(5, args -> {
            DrawRectangleRoundedLinesEx(rect(args.get(0)), toFloat(args.get(1)),
                    toInt(args.get(2)), toFloat(args.get(3)),
                    clr(args.get(4)));
            return null;
        }));

        // Triangles
        env.define("DrawTriangle", new NativeFunction(7, args -> {
            DrawTriangle(v2(args.get(0), args.get(1)), v2(args.get(2), args.get(3)),
                    v2(args.get(4), args.get(5)), clr(args.get(6)));
            return null;
        }));
        env.define("DrawTriangleLines", new NativeFunction(7, args -> {
            DrawTriangleLines(v2(args.get(0), args.get(1)), v2(args.get(2), args.get(3)),
                    v2(args.get(4), args.get(5)), clr(args.get(6)));
            return null;
        }));

        // Polygons
        env.define("DrawPoly", new NativeFunction(6, args -> {
            DrawPoly(v2(args.get(0), args.get(1)), toInt(args.get(2)),
                    toFloat(args.get(3)), toFloat(args.get(4)), clr(args.get(5)));
            return null;
        }));
        env.define("DrawPolyLines", new NativeFunction(6, args -> {
            DrawPolyLines(v2(args.get(0), args.get(1)), toInt(args.get(2)),
                    toFloat(args.get(3)), toFloat(args.get(4)), clr(args.get(5)));
            return null;
        }));
        env.define("DrawPolyLinesEx", new NativeFunction(7, args -> {
            DrawPolyLinesEx(v2(args.get(0), args.get(1)), toInt(args.get(2)),
                    toFloat(args.get(3)), toFloat(args.get(4)),
                    toFloat(args.get(5)), clr(args.get(6)));
            return null;
        }));

        // Text
        env.define("DrawText", new NativeFunction(5, args -> {
            DrawText(String.valueOf(args.get(0)), toInt(args.get(1)), toInt(args.get(2)),
                    toInt(args.get(3)), clr(args.get(4)));
            return null;
        }));
        env.define("DrawFPS", new NativeFunction(2, args -> {
            DrawFPS(toInt(args.get(0)), toInt(args.get(1)));
            return null;
        }));
        env.define("MeasureText", new NativeFunction(2, args
                -> (double) MeasureText(String.valueOf(args.get(0)), toInt(args.get(1)))));
    }

    /**
     * Registers all 3D drawing primitives.
     *
     * <p>
     * <b>Basic</b>:      {@code DrawGrid(slices, spacing)},
     * {@code DrawLine3D(x1, y1, z1, x2, y2, z2, color)},
     * {@code DrawPoint3D(x, y, z, color)}
     *
     * <p>
     * <b>Cube</b>:      {@code DrawCube(px, py, pz, w, h, d, color)},
     * {@code DrawCubeV(px, py, pz, sx, sy, sz, color)},
     * {@code DrawCubeWires(px, py, pz, w, h, d, color)},
     * {@code DrawCubeWiresV(px, py, pz, sx, sy, sz, color)}
     *
     * <p>
     * <b>Sphere</b>:      {@code DrawSphere(px, py, pz, radius, color)},
     * {@code DrawSphereEx(px, py, pz, radius, rings, slices, color)},
     * {@code DrawSphereWires(px, py, pz, radius, rings, slices, color)}
     *
     * <p>
     * <b>Cylinder &amp; capsule</b>:      {@code DrawCylinder(px, py, pz, rTop, rBot, height, slices, color)},
     * {@code DrawCylinderEx(sx, sy, sz, ex, ey, ez, rStart, rEnd, sides, color)},
     * {@code DrawCylinderWires(…)}, {@code DrawCylinderWiresEx(…)},
     * {@code DrawCapsule(sx, sy, sz, ex, ey, ez, radius, slices, rings, color)},
     * {@code DrawCapsuleWires(…)}
     *
     * <p>
     * <b>Other</b>:      {@code DrawPlane(px, py, pz, sx, sy, color)},
     * {@code DrawRay(ray, color)},
     * {@code DrawBoundingBox(box, color)},
     * {@code DrawBillboard(camera, texture, px, py, pz, size, color)}
     *
     * <p>
     * <b>Projection</b>:
     * {@code GetScreenToWorldRay(mouseX, mouseY, camera) -> Ray}
     */
    private void loadDraw3D(Environment env) {
        env.define("DrawGrid", new NativeFunction(2, args -> {
            DrawGrid(toInt(args.get(0)), toFloat(args.get(1)));
            return null;
        }));
        env.define("DrawLine3D", new NativeFunction(7, args -> {
            DrawLine3D(v3(args.get(0), args.get(1), args.get(2)),
                    v3(args.get(3), args.get(4), args.get(5)), clr(args.get(6)));
            return null;
        }));
        env.define("DrawPoint3D", new NativeFunction(4, args -> {
            DrawPoint3D(v3(args.get(0), args.get(1), args.get(2)), clr(args.get(3)));
            return null;
        }));

        // Cube
        env.define("DrawCube", new NativeFunction(7, args -> {
            DrawCube(v3(args.get(0), args.get(1), args.get(2)),
                    toFloat(args.get(3)), toFloat(args.get(4)),
                    toFloat(args.get(5)), clr(args.get(6)));
            return null;
        }));
        env.define("DrawCubeV", new NativeFunction(7, args -> {
            DrawCubeV(v3(args.get(0), args.get(1), args.get(2)),
                    v3(args.get(3), args.get(4), args.get(5)), clr(args.get(6)));
            return null;
        }));
        env.define("DrawCubeWires", new NativeFunction(7, args -> {
            DrawCubeWires(v3(args.get(0), args.get(1), args.get(2)),
                    toFloat(args.get(3)), toFloat(args.get(4)),
                    toFloat(args.get(5)), clr(args.get(6)));
            return null;
        }));
        env.define("DrawCubeWiresV", new NativeFunction(7, args -> {
            DrawCubeWiresV(v3(args.get(0), args.get(1), args.get(2)),
                    v3(args.get(3), args.get(4), args.get(5)), clr(args.get(6)));
            return null;
        }));

        // Sphere
        env.define("DrawSphere", new NativeFunction(5, args -> {
            DrawSphere(v3(args.get(0), args.get(1), args.get(2)),
                    toFloat(args.get(3)), clr(args.get(4)));
            return null;
        }));
        env.define("DrawSphereEx", new NativeFunction(7, args -> {
            DrawSphereEx(v3(args.get(0), args.get(1), args.get(2)), toFloat(args.get(3)),
                    toInt(args.get(4)), toInt(args.get(5)), clr(args.get(6)));
            return null;
        }));
        env.define("DrawSphereWires", new NativeFunction(7, args -> {
            DrawSphereWires(v3(args.get(0), args.get(1), args.get(2)), toFloat(args.get(3)),
                    toInt(args.get(4)), toInt(args.get(5)), clr(args.get(6)));
            return null;
        }));

        // Cylinder
        env.define("DrawCylinder", new NativeFunction(8, args -> {
            DrawCylinder(v3(args.get(0), args.get(1), args.get(2)),
                    toFloat(args.get(3)), toFloat(args.get(4)), toFloat(args.get(5)),
                    toInt(args.get(6)), clr(args.get(7)));
            return null;
        }));
        env.define("DrawCylinderEx", new NativeFunction(10, args -> {
            DrawCylinderEx(v3(args.get(0), args.get(1), args.get(2)),
                    v3(args.get(3), args.get(4), args.get(5)),
                    toFloat(args.get(6)), toFloat(args.get(7)),
                    toInt(args.get(8)), clr(args.get(9)));
            return null;
        }));
        env.define("DrawCylinderWires", new NativeFunction(8, args -> {
            DrawCylinderWires(v3(args.get(0), args.get(1), args.get(2)),
                    toFloat(args.get(3)), toFloat(args.get(4)), toFloat(args.get(5)),
                    toInt(args.get(6)), clr(args.get(7)));
            return null;
        }));
        env.define("DrawCylinderWiresEx", new NativeFunction(10, args -> {
            DrawCylinderWiresEx(v3(args.get(0), args.get(1), args.get(2)),
                    v3(args.get(3), args.get(4), args.get(5)),
                    toFloat(args.get(6)), toFloat(args.get(7)),
                    toInt(args.get(8)), clr(args.get(9)));
            return null;
        }));

        // Capsule
        env.define("DrawCapsule", new NativeFunction(10, args -> {
            DrawCapsule(v3(args.get(0), args.get(1), args.get(2)),
                    v3(args.get(3), args.get(4), args.get(5)),
                    toFloat(args.get(6)), toInt(args.get(7)),
                    toInt(args.get(8)), clr(args.get(9)));
            return null;
        }));
        env.define("DrawCapsuleWires", new NativeFunction(10, args -> {
            DrawCapsuleWires(v3(args.get(0), args.get(1), args.get(2)),
                    v3(args.get(3), args.get(4), args.get(5)),
                    toFloat(args.get(6)), toInt(args.get(7)),
                    toInt(args.get(8)), clr(args.get(9)));
            return null;
        }));

        // Plane and Ray
        env.define("DrawPlane", new NativeFunction(6, args -> {
            DrawPlane(v3(args.get(0), args.get(1), args.get(2)),
                    v2(args.get(3), args.get(4)), clr(args.get(5)));
            return null;
        }));
        env.define("DrawRay", new NativeFunction(2, args -> {
            DrawRay((Ray) args.get(0), clr(args.get(1)));
            return null;
        }));
        env.define("DrawBoundingBox", new NativeFunction(2, args -> {
            DrawBoundingBox((BoundingBox) args.get(0), clr(args.get(1)));
            return null;
        }));

        // Billboard
        env.define("DrawBillboard", new NativeFunction(7, args -> {
            DrawBillboard((Camera3D) args.get(0), (Texture) args.get(1),
                    v3(args.get(2), args.get(3), args.get(4)),
                    toFloat(args.get(5)), clr(args.get(6)));
            return null;
        }));

        // Ray casting
        env.define("GetScreenToWorldRay", new NativeFunction(3, args
                -> GetScreenToWorldRay(v2(args.get(0), args.get(1)), (Camera3D) args.get(2))));
    }

    /**
     * Registers texture loading, drawing, and render-texture functions.
     *
     * <p>
     * <b>Load / unload</b>: {@code LoadTexture(path) -> Texture},
     * {@code LoadTextureFromImage(image) -> Texture},
     * {@code LoadRenderTexture(width, height) -> RenderTexture},
     * {@code UnloadTexture(texture)}, {@code UnloadRenderTexture(renderTexture)}
     *
     * <p>
     * <b>Settings</b>:      {@code GenTextureMipmaps(texture)},
     * {@code SetTextureFilter(texture, filter)},
     * {@code SetTextureWrap(texture, wrap)}
     *
     * <p>
     * <b>Draw</b>:      {@code DrawTexture(texture, x, y, tint)},
     * {@code DrawTextureV(texture, x, y, tint)},
     * {@code DrawTextureEx(texture, x, y, rotation, scale, tint)},
     * {@code DrawTextureRec(texture, sourceRect, x, y, tint)},
     * {@code DrawTexturePro(texture, sourceRect, destRect, originX, originY, rotation, tint)}
     *
     * <p>
     * <b>Render texture</b>:
     * {@code BeginTextureMode(renderTexture)}, {@code EndTextureMode()}
     */
    private void loadTextures(Environment env) {
        env.define("LoadTexture", new NativeFunction(1, args
                -> LoadTexture(String.valueOf(args.get(0)))));
        env.define("LoadTextureFromImage", new NativeFunction(1, args
                -> LoadTextureFromImage((Image) args.get(0))));
        env.define("LoadRenderTexture", new NativeFunction(2, args
                -> LoadRenderTexture(toInt(args.get(0)), toInt(args.get(1)))));
        env.define("UnloadTexture", new NativeFunction(1, args -> {
            UnloadTexture((Texture) args.get(0));
            return null;
        }));
        env.define("UnloadRenderTexture", new NativeFunction(1, args -> {
            UnloadRenderTexture((RenderTexture) args.get(0));
            return null;
        }));
        env.define("GenTextureMipmaps", new NativeFunction(1, args -> {
            GenTextureMipmaps((Texture) args.get(0));
            return null;
        }));
        env.define("SetTextureFilter", new NativeFunction(2, args -> {
            SetTextureFilter((Texture) args.get(0), toInt(args.get(1)));
            return null;
        }));
        env.define("SetTextureWrap", new NativeFunction(2, args -> {
            SetTextureWrap((Texture) args.get(0), toInt(args.get(1)));
            return null;
        }));

        // Drawing textures
        env.define("DrawTexture", new NativeFunction(4, args -> {
            DrawTexture((Texture) args.get(0), toInt(args.get(1)),
                    toInt(args.get(2)), clr(args.get(3)));
            return null;
        }));
        env.define("DrawTextureV", new NativeFunction(4, args -> {
            DrawTextureV((Texture) args.get(0), v2(args.get(1), args.get(2)),
                    clr(args.get(3)));
            return null;
        }));
        env.define("DrawTextureEx", new NativeFunction(6, args -> {
            DrawTextureEx((Texture) args.get(0), v2(args.get(1), args.get(2)),
                    toFloat(args.get(3)), toFloat(args.get(4)),
                    clr(args.get(5)));
            return null;
        }));
        env.define("DrawTextureRec", new NativeFunction(5, args -> {
            DrawTextureRec((Texture) args.get(0), rect(args.get(1)),
                    v2(args.get(2), args.get(3)), clr(args.get(4)));
            return null;
        }));
        env.define("DrawTexturePro", new NativeFunction(7, args -> {
            DrawTexturePro((Texture) args.get(0), rect(args.get(1)),
                    rect(args.get(2)), v2(args.get(3), args.get(4)),
                    toFloat(args.get(5)), clr(args.get(6)));
            return null;
        }));

        // Render texture mode
        env.define("BeginTextureMode", new NativeFunction(1, args -> {
            BeginTextureMode((RenderTexture) args.get(0));
            return null;
        }));
        env.define("EndTextureMode", new NativeFunction(0, args -> {
            EndTextureMode();
            return null;
        }));
    }

    /**
     * Registers image loading, generation, and manipulation functions.
     *
     * <p>
     * <b>Load / unload</b>: {@code LoadImage(path) -> Image},
     * {@code LoadImageFromTexture(texture) -> Image},      {@code UnloadImage(image)},
     * {@code ExportImage(image, path) -> Boolean},
     * {@code ImageCopy(image) -> Image}
     *
     * <p>
     * <b>Transform (in-place)</b>:      {@code ImageToPOT(image, fillColor)},
     * {@code ImageFormat(image, pixelFormat)},
     * {@code ImageCrop(image, rect)},
     * {@code ImageAlphaCrop(image, threshold)},
     * {@code ImageAlphaClear(image, color, threshold)},
     * {@code ImageAlphaMask(image, alphaMask)},
     * {@code ImageAlphaPremultiply(image)},
     * {@code ImageBlurGaussian(image, blurSize)},
     * {@code ImageResize(image, w, h)},
     * {@code ImageResizeNN(image, w, h)},
     * {@code ImageFlipHorizontal(image)}, {@code ImageFlipVertical(image)},
     * {@code ImageRotate(image, degrees)},
     * {@code ImageColorBrightness(image, value)},
     * {@code ImageColorContrast(image, contrast)},
     * {@code ImageColorInvert(image)}, {@code ImageColorGrayscale(image)},
     * {@code ImageColorTint(image, color)},
     * {@code ImageClearBackground(image, color)}
     *
     * <p>
     * <b>Draw into image</b>:      {@code ImageDrawPixel(image, x, y, color)},
     * {@code ImageDrawRectangle(image, x, y, w, h, color)},
     * {@code ImageDrawText(image, text, x, y, fontSize, color)}
     *
     * <p>
     * <b>Generate</b>: {@code GenImageColor(w, h, color) -> Image},
     * {@code GenImageGradientLinear(w, h, direction, startColor, endColor) -> Image},
     * {@code GenImageGradientRadial(w, h, density, innerColor, outerColor) -> Image}
     */
    private void loadImages(Environment env) {
        env.define("LoadImage", new NativeFunction(1, args
                -> LoadImage(String.valueOf(args.get(0)))));
        env.define("LoadImageFromTexture", new NativeFunction(1, args
                -> LoadImageFromTexture((Texture) args.get(0))));
        env.define("UnloadImage", new NativeFunction(1, args -> {
            UnloadImage((Image) args.get(0));
            return null;
        }));
        env.define("ExportImage", new NativeFunction(2, args
                -> ExportImage((Image) args.get(0), String.valueOf(args.get(1)))));
        env.define("ImageCopy", new NativeFunction(1, args
                -> ImageCopy((Image) args.get(0))));

        // Image manipulation (in-place)
        env.define("ImageToPOT", new NativeFunction(2, args -> {
            ImageToPOT((Image) args.get(0), clr(args.get(1)));
            return null;
        }));
        env.define("ImageFormat", new NativeFunction(2, args -> {
            ImageFormat((Image) args.get(0), toInt(args.get(1)));
            return null;
        }));
        env.define("ImageCrop", new NativeFunction(2, args -> {
            ImageCrop((Image) args.get(0), rect(args.get(1)));
            return null;
        }));
        env.define("ImageAlphaCrop", new NativeFunction(2, args -> {
            ImageAlphaCrop((Image) args.get(0), toFloat(args.get(1)));
            return null;
        }));
        env.define("ImageAlphaClear", new NativeFunction(3, args -> {
            ImageAlphaClear((Image) args.get(0), clr(args.get(1)),
                    toFloat(args.get(2)));
            return null;
        }));
        env.define("ImageAlphaMask", new NativeFunction(2, args -> {
            ImageAlphaMask((Image) args.get(0), (Image) args.get(1));
            return null;
        }));
        env.define("ImageAlphaPremultiply", new NativeFunction(1, args -> {
            ImageAlphaPremultiply((Image) args.get(0));
            return null;
        }));
        env.define("ImageBlurGaussian", new NativeFunction(2, args -> {
            ImageBlurGaussian((Image) args.get(0), toInt(args.get(1)));
            return null;
        }));
        env.define("ImageResize", new NativeFunction(3, args -> {
            ImageResize((Image) args.get(0), toInt(args.get(1)), toInt(args.get(2)));
            return null;
        }));
        env.define("ImageResizeNN", new NativeFunction(3, args -> {
            ImageResizeNN((Image) args.get(0), toInt(args.get(1)), toInt(args.get(2)));
            return null;
        }));
        env.define("ImageFlipHorizontal", new NativeFunction(1, args -> {
            ImageFlipHorizontal((Image) args.get(0));
            return null;
        }));
        env.define("ImageFlipVertical", new NativeFunction(1, args -> {
            ImageFlipVertical((Image) args.get(0));
            return null;
        }));
        env.define("ImageRotate", new NativeFunction(2, args -> {
            ImageRotate((Image) args.get(0), toInt(args.get(1)));
            return null;
        }));
        env.define("ImageColorBrightness", new NativeFunction(2, args -> {
            ImageColorBrightness((Image) args.get(0), toInt(args.get(1)));
            return null;
        }));
        env.define("ImageColorContrast", new NativeFunction(2, args -> {
            ImageColorContrast((Image) args.get(0), toFloat(args.get(1)));
            return null;
        }));
        env.define("ImageColorInvert", new NativeFunction(1, args -> {
            ImageColorInvert((Image) args.get(0));
            return null;
        }));
        env.define("ImageColorGrayscale", new NativeFunction(1, args -> {
            ImageColorGrayscale((Image) args.get(0));
            return null;
        }));
        env.define("ImageColorTint", new NativeFunction(2, args -> {
            ImageColorTint((Image) args.get(0), clr(args.get(1)));
            return null;
        }));
        env.define("ImageClearBackground", new NativeFunction(2, args -> {
            ImageClearBackground((Image) args.get(0), clr(args.get(1)));
            return null;
        }));

        // Image draw operations
        env.define("ImageDrawPixel", new NativeFunction(4, args -> {
            ImageDrawPixel((Image) args.get(0), toInt(args.get(1)),
                    toInt(args.get(2)), clr(args.get(3)));
            return null;
        }));
        env.define("ImageDrawRectangle", new NativeFunction(6, args -> {
            ImageDrawRectangle((Image) args.get(0), toInt(args.get(1)), toInt(args.get(2)),
                    toInt(args.get(3)), toInt(args.get(4)),
                    clr(args.get(5)));
            return null;
        }));
        env.define("ImageDrawText", new NativeFunction(6, args -> {
            ImageDrawText((Image) args.get(0), String.valueOf(args.get(1)),
                    toInt(args.get(2)), toInt(args.get(3)),
                    toInt(args.get(4)), clr(args.get(5)));
            return null;
        }));

        // Image generation
        env.define("GenImageColor", new NativeFunction(3, args
                -> GenImageColor(toInt(args.get(0)), toInt(args.get(1)), clr(args.get(2)))));
        env.define("GenImageGradientLinear", new NativeFunction(5, args
                -> GenImageGradientLinear(toInt(args.get(0)), toInt(args.get(1)),
                        toInt(args.get(2)), clr(args.get(3)), clr(args.get(4)))));
        env.define("GenImageGradientRadial", new NativeFunction(5, args
                -> GenImageGradientRadial(toInt(args.get(0)), toInt(args.get(1)),
                        toFloat(args.get(2)), clr(args.get(3)), clr(args.get(4)))));
    }

    /**
     * Registers font loading and advanced text drawing functions.
     *
     * <ul>
     * <li>{@code LoadFont(path) -> Font}, {@code GetFontDefault() -> Font},
     * {@code UnloadFont(font)}, {@code IsFontValid(font) -> Boolean}</li>
     * <li>{@code DrawTextEx(font, text, x, y, fontSize, spacing, color)} — draw
     * with custom font</li>
     * <li>{@code DrawTextPro(font, text, posX, posY, originX, originY, rotation, fontSize, spacing, color)}</li>
     * <li>{@code MeasureTextEx(font, text, fontSize, spacing) -> Vector2} — use
     * {@code Vector2X/Y} to read</li>
     * <li>{@code SetTextLineSpacing(spacing)} — global line spacing for
     * multi-line text</li>
     * </ul>
     */
    private void loadFonts(Environment env) {
        env.define("LoadFont", new NativeFunction(1, args
                -> LoadFont(String.valueOf(args.get(0)))));
        env.define("GetFontDefault", new NativeFunction(0, args -> GetFontDefault()));
        env.define("UnloadFont", new NativeFunction(1, args -> {
            UnloadFont((Font) args.get(0));
            return null;
        }));
        env.define("IsFontValid", new NativeFunction(1, args
                -> IsFontValid((Font) args.get(0))));

        env.define("DrawTextEx", new NativeFunction(7, args -> {
            DrawTextEx((Font) args.get(0), String.valueOf(args.get(1)),
                    v2(args.get(2), args.get(3)), toFloat(args.get(4)),
                    toFloat(args.get(5)), clr(args.get(6)));
            return null;
        }));
        // DrawTextPro(font, text, posX, posY, originX, originY, rotation, fontSize, spacing, color)
        env.define("DrawTextPro", new NativeFunction(10, args -> {
            DrawTextPro((Font) args.get(0), String.valueOf(args.get(1)),
                    v2(args.get(2), args.get(3)), v2(args.get(4), args.get(5)),
                    toFloat(args.get(6)), toFloat(args.get(7)),
                    toFloat(args.get(8)), clr(args.get(9)));
            return null;
        }));
        env.define("MeasureTextEx", new NativeFunction(4, args
                -> MeasureTextEx((Font) args.get(0), String.valueOf(args.get(1)),
                        toFloat(args.get(2)), toFloat(args.get(3)))));
        env.define("SetTextLineSpacing", new NativeFunction(1, args -> {
            SetTextLineSpacing(toInt(args.get(0)));
            return null;
        }));
    }

    /**
     * Registers GLSL shader loading and uniform-setting functions.
     *
     * <ul>
     * <li>{@code LoadShader(vsPath, fsPath) -> Shader} — pass {@code null} for
     * a stage to use the default</li>
     * <li>{@code LoadShaderFromMemory(vsCode, fsCode) -> Shader}</li>
     * <li>{@code IsShaderValid(shader) -> Boolean},
     * {@code UnloadShader(shader)}</li>
     * <li>{@code GetShaderLocation(shader, uniformName) -> Number}</li>
     * <li>{@code GetShaderLocationAttrib(shader, attribName) -> Number}</li>
     * <li>{@code SetShaderValueInt(shader, location, intValue)} — sets a
     * {@code SHADER_UNIFORM_INT}</li>
     * <li>{@code SetShaderValueFloat(shader, location, floatValue)} — sets a
     * {@code SHADER_UNIFORM_FLOAT}</li>
     * <li>{@code SetShaderValueTexture(shader, location, texture)}</li>
     * <li>{@code BeginShaderMode(shader)}, {@code EndShaderMode()}</li>
     * </ul>
     */
    private void loadShaders(Environment env) {
        env.define("LoadShader", new NativeFunction(2, args
                -> LoadShader(shaderPath(args.get(0)), shaderPath(args.get(1)))));
        env.define("LoadShaderFromMemory", new NativeFunction(2, args
                -> LoadShaderFromMemory(shaderPath(args.get(0)), shaderPath(args.get(1)))));
        env.define("IsShaderValid", new NativeFunction(1, args
                -> IsShaderValid((Shader) args.get(0))));
        env.define("UnloadShader", new NativeFunction(1, args -> {
            UnloadShader((Shader) args.get(0));
            return null;
        }));
        env.define("GetShaderLocation", new NativeFunction(2, args
                -> (double) GetShaderLocation((Shader) args.get(0), String.valueOf(args.get(1)))));
        env.define("GetShaderLocationAttrib", new NativeFunction(2, args
                -> (double) GetShaderLocationAttrib((Shader) args.get(0), String.valueOf(args.get(1)))));

        // SetShaderValue variants using native pointers
        env.define("SetShaderValueInt", new NativeFunction(3, args -> {
            try (IntPointer p = new IntPointer(1).put(toInt(args.get(2)))) {
                SetShaderValue((Shader) args.get(0), toInt(args.get(1)), p, 4);
            }
            return null;
        }));
        env.define("SetShaderValueFloat", new NativeFunction(3, args -> {
            try (FloatPointer p = new FloatPointer(1).put(toFloat(args.get(2)))) {
                SetShaderValue((Shader) args.get(0), toInt(args.get(1)), p, 0);
            }
            return null;
        }));
        env.define("SetShaderValueTexture", new NativeFunction(3, args -> {
            SetShaderValueTexture((Shader) args.get(0), toInt(args.get(1)),
                    (Texture) args.get(2));
            return null;
        }));

        env.define("BeginShaderMode", new NativeFunction(1, args -> {
            BeginShaderMode((Shader) args.get(0));
            return null;
        }));
        env.define("EndShaderMode", new NativeFunction(0, args -> {
            EndShaderMode();
            return null;
        }));
    }

    /**
     * Registers blend-mode and scissor-mode functions.
     *
     * <ul>
     * <li>{@code BeginBlendMode(mode)} — use a {@code BLEND_*} constant</li>
     * <li>{@code EndBlendMode()}</li>
     * <li>{@code BeginScissorMode(x, y, width, height)} — clip drawing to a
     * rectangle</li>
     * <li>{@code EndScissorMode()}</li>
     * </ul>
     */
    private void loadBlendScissor(Environment env) {
        env.define("BeginBlendMode", new NativeFunction(1, args -> {
            BeginBlendMode(toInt(args.get(0)));
            return null;
        }));
        env.define("EndBlendMode", new NativeFunction(0, args -> {
            EndBlendMode();
            return null;
        }));
        env.define("BeginScissorMode", new NativeFunction(4, args -> {
            BeginScissorMode(toInt(args.get(0)), toInt(args.get(1)),
                    toInt(args.get(2)), toInt(args.get(3)));
            return null;
        }));
        env.define("EndScissorMode", new NativeFunction(0, args -> {
            EndScissorMode();
            return null;
        }));
    }

    /**
     * Registers keyboard and mouse input functions.
     *
     * <p>
     * <b>Keyboard</b>: {@code IsKeyPressed(key) -> Boolean},
     * {@code IsKeyPressedRepeat(key) -> Boolean},
     * {@code IsKeyDown(key) -> Boolean}, {@code IsKeyReleased(key) -> Boolean},
     * {@code IsKeyUp(key) -> Boolean}, {@code GetKeyPressed() -> Number} — next
     * queued key code, {@code GetCharPressed() -> Number} — next queued Unicode
     * codepoint, {@code SetExitKey(key)} — key that closes the window (default
     * ESC)
     *
     * <p>
     * <b>Mouse buttons</b>:
     * {@code IsMouseButtonPressed/Down/Released/Up(button) -> Boolean}
     *
     * <p>
     * <b>Mouse position &amp; wheel</b>: {@code GetMouseX() -> Number},
     * {@code GetMouseY() -> Number}, {@code GetMousePosition() -> Vector2},
     * {@code GetMouseDelta() -> Vector2},
     * {@code GetMouseWheelMove() -> Number},      {@code SetMousePosition(x, y)}, {@code SetMouseOffset(x, y)},
     * {@code SetMouseScale(scaleX, scaleY)},
     * {@code SetMouseCursor(cursor)} — use a {@code MOUSE_CURSOR_*} constant
     */
    private void loadInput(Environment env) {
        // Keyboard
        env.define("IsKeyPressed", new NativeFunction(1, args -> IsKeyPressed(toInt(args.get(0)))));
        env.define("IsKeyPressedRepeat", new NativeFunction(1, args -> IsKeyPressedRepeat(toInt(args.get(0)))));
        env.define("IsKeyDown", new NativeFunction(1, args -> IsKeyDown(toInt(args.get(0)))));
        env.define("IsKeyReleased", new NativeFunction(1, args -> IsKeyReleased(toInt(args.get(0)))));
        env.define("IsKeyUp", new NativeFunction(1, args -> IsKeyUp(toInt(args.get(0)))));
        env.define("GetKeyPressed", new NativeFunction(0, args -> (double) GetKeyPressed()));
        env.define("GetCharPressed", new NativeFunction(0, args -> (double) GetCharPressed()));
        env.define("SetExitKey", new NativeFunction(1, args -> {
            SetExitKey(toInt(args.get(0)));
            return null;
        }));

        // Mouse buttons
        env.define("IsMouseButtonPressed", new NativeFunction(1, args -> IsMouseButtonPressed(toInt(args.get(0)))));
        env.define("IsMouseButtonDown", new NativeFunction(1, args -> IsMouseButtonDown(toInt(args.get(0)))));
        env.define("IsMouseButtonReleased", new NativeFunction(1, args -> IsMouseButtonReleased(toInt(args.get(0)))));
        env.define("IsMouseButtonUp", new NativeFunction(1, args -> IsMouseButtonUp(toInt(args.get(0)))));

        // Mouse position
        env.define("GetMouseX", new NativeFunction(0, args -> (double) GetMouseX()));
        env.define("GetMouseY", new NativeFunction(0, args -> (double) GetMouseY()));
        env.define("GetMousePosition", new NativeFunction(0, args -> GetMousePosition()));
        env.define("GetMouseDelta", new NativeFunction(0, args -> GetMouseDelta()));
        env.define("GetMouseWheelMove", new NativeFunction(0, args -> (double) GetMouseWheelMove()));
        env.define("SetMousePosition", new NativeFunction(2, args -> {
            SetMousePosition(toInt(args.get(0)), toInt(args.get(1)));
            return null;
        }));
        env.define("SetMouseOffset", new NativeFunction(2, args -> {
            SetMouseOffset(toInt(args.get(0)), toInt(args.get(1)));
            return null;
        }));
        env.define("SetMouseScale", new NativeFunction(2, args -> {
            SetMouseScale(toFloat(args.get(0)), toFloat(args.get(1)));
            return null;
        }));
        env.define("SetMouseCursor", new NativeFunction(1, args -> {
            SetMouseCursor(toInt(args.get(0)));
            return null;
        }));
    }

    /**
     * Registers gamepad input functions.
     *
     * <ul>
     * <li>{@code IsGamepadAvailable(gamepad) -> Boolean}</li>
     * <li>{@code IsGamepadButtonPressed/Down/Released(gamepad, button) -> Boolean}
     * — use {@code GAMEPAD_BUTTON_*}</li>
     * <li>{@code GetGamepadAxisCount(gamepad) -> Number}</li>
     * <li>{@code GetGamepadAxisMovement(gamepad, axis) -> Number} — use
     * {@code GAMEPAD_AXIS_*}, range -1..1</li>
     * </ul>
     */
    private void loadGamepad(Environment env) {
        env.define("IsGamepadAvailable", new NativeFunction(1, args
                -> IsGamepadAvailable(toInt(args.get(0)))));
        env.define("IsGamepadButtonPressed", new NativeFunction(2, args
                -> IsGamepadButtonPressed(toInt(args.get(0)), toInt(args.get(1)))));
        env.define("IsGamepadButtonDown", new NativeFunction(2, args
                -> IsGamepadButtonDown(toInt(args.get(0)), toInt(args.get(1)))));
        env.define("IsGamepadButtonReleased", new NativeFunction(2, args
                -> IsGamepadButtonReleased(toInt(args.get(0)), toInt(args.get(1)))));
        env.define("GetGamepadAxisCount", new NativeFunction(1, args
                -> (double) GetGamepadAxisCount(toInt(args.get(0)))));
        env.define("GetGamepadAxisMovement", new NativeFunction(2, args
                -> (double) GetGamepadAxisMovement(toInt(args.get(0)), toInt(args.get(1)))));
    }

    /**
     * Registers touch-input functions (mobile / touch screens).
     *
     * <ul>
     * <li>{@code GetTouchX() -> Number}, {@code GetTouchY() -> Number} — first
     * touch point</li>
     * <li>{@code GetTouchPointCount() -> Number} — number of active touch
     * points</li>
     * <li>{@code GetTouchPosition(index) -> Vector2} — position of touch point
     * at index</li>
     * </ul>
     */
    private void loadTouch(Environment env) {
        env.define("GetTouchX", new NativeFunction(0, args -> (double) GetTouchX()));
        env.define("GetTouchY", new NativeFunction(0, args -> (double) GetTouchY()));
        env.define("GetTouchPointCount", new NativeFunction(0, args -> (double) GetTouchPointCount()));
        env.define("GetTouchPosition", new NativeFunction(1, args
                -> GetTouchPosition(toInt(args.get(0)))));
    }

    /**
     * Registers audio device, sound, wave, and music-streaming functions.
     *
     * <p>
     * <b>Device</b>:      {@code InitAudioDevice()}, {@code CloseAudioDevice()},
     * {@code IsAudioDeviceReady() -> Boolean},
     * {@code SetMasterVolume(volume)}, {@code GetMasterVolume() -> Number}
     *
     * <p>
     * <b>Sound</b>: {@code LoadSound(path) -> Sound},
     * {@code LoadSoundFromWave(wave) -> Sound},
     * {@code LoadSoundAlias(sound) -> Sound},      {@code UnloadSound(sound)}, {@code UnloadSoundAlias(sound)},
     * {@code PlaySound(sound)}, {@code StopSound(sound)},
     * {@code PauseSound(sound)}, {@code ResumeSound(sound)},
     * {@code IsSoundPlaying(sound) -> Boolean},      {@code SetSoundVolume(sound, volume)}, {@code SetSoundPitch(sound, pitch)},
     * {@code SetSoundPan(sound, pan)}
     *
     * <p>
     * <b>Wave</b>: {@code LoadWave(path) -> Wave},
     * {@code WaveCopy(wave) -> Wave}, {@code UnloadWave(wave)}
     *
     * <p>
     * <b>Music streaming</b>: {@code LoadMusicStream(path) -> Music}, {@code UnloadMusicStream(music)},
     * {@code PlayMusicStream(music)}, {@code StopMusicStream(music)},
     * {@code PauseMusicStream(music)}, {@code ResumeMusicStream(music)},
     * {@code UpdateMusicStream(music)} — call every frame while playing,
     * {@code IsMusicStreamPlaying(music) -> Boolean},      {@code SeekMusicStream(music, position)},
     * {@code GetMusicTimeLength(music) -> Number},
     * {@code GetMusicTimePlayed(music) -> Number},      {@code SetMusicVolume(music, volume)}, {@code SetMusicPitch(music, pitch)},
     * {@code SetMusicPan(music, pan)}
     */
    private void loadAudio(Environment env) {
        // Device
        env.define("InitAudioDevice", new NativeFunction(0, args -> {
            InitAudioDevice();
            return null;
        }));
        env.define("CloseAudioDevice", new NativeFunction(0, args -> {
            CloseAudioDevice();
            return null;
        }));
        env.define("IsAudioDeviceReady", new NativeFunction(0, args -> IsAudioDeviceReady()));
        env.define("SetMasterVolume", new NativeFunction(1, args -> {
            SetMasterVolume(toFloat(args.get(0)));
            return null;
        }));
        env.define("GetMasterVolume", new NativeFunction(0, args -> (double) GetMasterVolume()));

        // Sound
        env.define("LoadSound", new NativeFunction(1, args
                -> LoadSound(String.valueOf(args.get(0)))));
        env.define("LoadSoundFromWave", new NativeFunction(1, args
                -> LoadSoundFromWave((Wave) args.get(0))));
        env.define("LoadSoundAlias", new NativeFunction(1, args
                -> LoadSoundAlias((Sound) args.get(0))));
        env.define("UnloadSound", new NativeFunction(1, args -> {
            UnloadSound((Sound) args.get(0));
            return null;
        }));
        env.define("UnloadSoundAlias", new NativeFunction(1, args -> {
            UnloadSoundAlias((Sound) args.get(0));
            return null;
        }));
        env.define("PlaySound", new NativeFunction(1, args -> {
            PlaySound((Sound) args.get(0));
            return null;
        }));
        env.define("StopSound", new NativeFunction(1, args -> {
            StopSound((Sound) args.get(0));
            return null;
        }));
        env.define("PauseSound", new NativeFunction(1, args -> {
            PauseSound((Sound) args.get(0));
            return null;
        }));
        env.define("ResumeSound", new NativeFunction(1, args -> {
            ResumeSound((Sound) args.get(0));
            return null;
        }));
        env.define("IsSoundPlaying", new NativeFunction(1, args
                -> IsSoundPlaying((Sound) args.get(0))));
        env.define("SetSoundVolume", new NativeFunction(2, args -> {
            SetSoundVolume((Sound) args.get(0), toFloat(args.get(1)));
            return null;
        }));
        env.define("SetSoundPitch", new NativeFunction(2, args -> {
            SetSoundPitch((Sound) args.get(0), toFloat(args.get(1)));
            return null;
        }));
        env.define("SetSoundPan", new NativeFunction(2, args -> {
            SetSoundPan((Sound) args.get(0), toFloat(args.get(1)));
            return null;
        }));

        // Wave
        env.define("LoadWave", new NativeFunction(1, args
                -> LoadWave(String.valueOf(args.get(0)))));
        env.define("WaveCopy", new NativeFunction(1, args
                -> WaveCopy((Wave) args.get(0))));
        env.define("UnloadWave", new NativeFunction(1, args -> {
            UnloadWave((Wave) args.get(0));
            return null;
        }));

        // Music
        env.define("LoadMusicStream", new NativeFunction(1, args
                -> LoadMusicStream(String.valueOf(args.get(0)))));
        env.define("UnloadMusicStream", new NativeFunction(1, args -> {
            UnloadMusicStream((Music) args.get(0));
            return null;
        }));
        env.define("PlayMusicStream", new NativeFunction(1, args -> {
            PlayMusicStream((Music) args.get(0));
            return null;
        }));
        env.define("StopMusicStream", new NativeFunction(1, args -> {
            StopMusicStream((Music) args.get(0));
            return null;
        }));
        env.define("PauseMusicStream", new NativeFunction(1, args -> {
            PauseMusicStream((Music) args.get(0));
            return null;
        }));
        env.define("ResumeMusicStream", new NativeFunction(1, args -> {
            ResumeMusicStream((Music) args.get(0));
            return null;
        }));
        env.define("UpdateMusicStream", new NativeFunction(1, args -> {
            UpdateMusicStream((Music) args.get(0));
            return null;
        }));
        env.define("IsMusicStreamPlaying", new NativeFunction(1, args
                -> IsMusicStreamPlaying((Music) args.get(0))));
        env.define("SeekMusicStream", new NativeFunction(2, args -> {
            SeekMusicStream((Music) args.get(0), toFloat(args.get(1)));
            return null;
        }));
        env.define("GetMusicTimeLength", new NativeFunction(1, args
                -> (double) GetMusicTimeLength((Music) args.get(0))));
        env.define("GetMusicTimePlayed", new NativeFunction(1, args
                -> (double) GetMusicTimePlayed((Music) args.get(0))));
        env.define("SetMusicVolume", new NativeFunction(2, args -> {
            SetMusicVolume((Music) args.get(0), toFloat(args.get(1)));
            return null;
        }));
        env.define("SetMusicPitch", new NativeFunction(2, args -> {
            SetMusicPitch((Music) args.get(0), toFloat(args.get(1)));
            return null;
        }));
        env.define("SetMusicPan", new NativeFunction(2, args -> {
            SetMusicPan((Music) args.get(0), toFloat(args.get(1)));
            return null;
        }));
    }

    /**
     * Registers file-system query and URL functions.
     *
     * <ul>
     * <li>{@code FileExists(path) -> Boolean},
     * {@code DirectoryExists(path) -> Boolean}</li>
     * <li>{@code GetFileLength(path) -> Number} — size in bytes</li>
     * <li>{@code GetFileModTime(path) -> Number} — modification time as Unix
     * timestamp</li>
     * <li>{@code GetFileExtension(path) -> String} — e.g. {@code ".png"}</li>
     * <li>{@code GetFileName(path) -> String} — filename with extension</li>
     * <li>{@code GetFileNameWithoutExt(path) -> String}</li>
     * <li>{@code GetDirectoryPath(path) -> String} — parent directory</li>
     * <li>{@code GetWorkingDirectory() -> String} — current working
     * directory</li>
     * <li>{@code IsFileDropped() -> Boolean} — true if files were drag-dropped
     * onto the window</li>
     * <li>{@code OpenURL(url)} — open URL in the default system browser</li>
     * </ul>
     */
    private void loadFileIO(Environment env) {
        env.define("FileExists", new NativeFunction(1, args
                -> FileExists(String.valueOf(args.get(0)))));
        env.define("DirectoryExists", new NativeFunction(1, args
                -> DirectoryExists(String.valueOf(args.get(0)))));
        env.define("GetFileLength", new NativeFunction(1, args
                -> (double) GetFileLength(String.valueOf(args.get(0)))));
        env.define("GetFileModTime", new NativeFunction(1, args
                -> (double) GetFileModTime(String.valueOf(args.get(0)))));
        env.define("GetFileExtension", new NativeFunction(1, args
                -> GetFileExtension(String.valueOf(args.get(0)))));
        env.define("GetFileName", new NativeFunction(1, args
                -> GetFileName(String.valueOf(args.get(0)))));
        env.define("GetFileNameWithoutExt", new NativeFunction(1, args
                -> GetFileNameWithoutExt(String.valueOf(args.get(0)))));
        env.define("GetDirectoryPath", new NativeFunction(1, args
                -> GetDirectoryPath(String.valueOf(args.get(0)))));
        env.define("GetWorkingDirectory", new NativeFunction(0, args
                -> bpStr(GetWorkingDirectory())));
        env.define("IsFileDropped", new NativeFunction(0, args -> IsFileDropped()));
        env.define("OpenURL", new NativeFunction(1, args -> {
            OpenURL(String.valueOf(args.get(0)));
            return null;
        }));
    }

    /**
     * Registers 2D and 3D collision-detection functions.
     *
     * <p>
     * <b>2D</b>: {@code CheckCollisionRecs(rect1, rect2) -> Boolean},
     * {@code CheckCollisionCircles(cx1, cy1, r1, cx2, cy2, r2) -> Boolean},
     * {@code CheckCollisionCircleRec(cx, cy, r, rect) -> Boolean},
     * {@code CheckCollisionPointRec(px, py, rect) -> Boolean},
     * {@code CheckCollisionPointCircle(px, py, cx, cy, r) -> Boolean},
     * {@code CheckCollisionPointTriangle(px, py, x1, y1, x2, y2, x3, y3) -> Boolean},
     * {@code GetCollisionRec(rect1, rect2) -> Rectangle} — overlapping area
     *
     * <p>
     * <b>3D ray</b>:
     * {@code GetRayCollisionSphere(ray, cx, cy, cz, radius) -> RayCollision},
     * {@code GetRayCollisionBox(ray, boundingBox) -> RayCollision},
     * {@code GetRayCollisionTriangle(ray, x1,y1,z1, x2,y2,z2, x3,y3,z3) -> RayCollision},
     * {@code GetRayCollisionQuad(ray, x1,y1,z1, x2,y2,z2, x3,y3,z3, x4,y4,z4) -> RayCollision}
     *
     * <p>
     * Read a {@code RayCollision} with: {@code RayCollisionHit(rc) -> Boolean},
     * {@code RayCollisionDistance(rc) -> Number},
     * {@code RayCollisionPoint(rc) -> Vector3}
     */
    private void loadCollision(Environment env) {
        env.define("CheckCollisionRecs", new NativeFunction(2, args
                -> CheckCollisionRecs(rect(args.get(0)), rect(args.get(1)))));
        // CheckCollisionCircles(cx1,cy1,r1, cx2,cy2,r2)
        env.define("CheckCollisionCircles", new NativeFunction(6, args
                -> CheckCollisionCircles(v2(args.get(0), args.get(1)), toFloat(args.get(2)),
                        v2(args.get(3), args.get(4)), toFloat(args.get(5)))));
        // CheckCollisionCircleRec(cx,cy,r, rect)
        env.define("CheckCollisionCircleRec", new NativeFunction(4, args
                -> CheckCollisionCircleRec(v2(args.get(0), args.get(1)), toFloat(args.get(2)),
                        rect(args.get(3)))));
        // CheckCollisionPointRec(px,py, rect)
        env.define("CheckCollisionPointRec", new NativeFunction(3, args
                -> CheckCollisionPointRec(v2(args.get(0), args.get(1)), rect(args.get(2)))));
        // CheckCollisionPointCircle(px,py, cx,cy,r)
        env.define("CheckCollisionPointCircle", new NativeFunction(5, args
                -> CheckCollisionPointCircle(v2(args.get(0), args.get(1)),
                        v2(args.get(2), args.get(3)), toFloat(args.get(4)))));
        // CheckCollisionPointTriangle(px,py, x1,y1, x2,y2, x3,y3)
        env.define("CheckCollisionPointTriangle", new NativeFunction(8, args
                -> CheckCollisionPointTriangle(v2(args.get(0), args.get(1)),
                        v2(args.get(2), args.get(3)),
                        v2(args.get(4), args.get(5)),
                        v2(args.get(6), args.get(7)))));
        env.define("GetCollisionRec", new NativeFunction(2, args
                -> GetCollisionRec(rect(args.get(0)), rect(args.get(1)))));

        // 3D collision
        // GetRayCollisionSphere(ray, cx,cy,cz, radius)
        env.define("GetRayCollisionSphere", new NativeFunction(5, args
                -> GetRayCollisionSphere((Ray) args.get(0),
                        v3(args.get(1), args.get(2), args.get(3)),
                        toFloat(args.get(4)))));
        env.define("GetRayCollisionBox", new NativeFunction(2, args
                -> GetRayCollisionBox((Ray) args.get(0), (BoundingBox) args.get(1))));
        // GetRayCollisionTriangle(ray, x1,y1,z1, x2,y2,z2, x3,y3,z3)
        env.define("GetRayCollisionTriangle", new NativeFunction(10, args
                -> GetRayCollisionTriangle((Ray) args.get(0),
                        v3(args.get(1), args.get(2), args.get(3)),
                        v3(args.get(4), args.get(5), args.get(6)),
                        v3(args.get(7), args.get(8), args.get(9)))));
        // GetRayCollisionQuad(ray, x1,y1,z1, x2,y2,z2, x3,y3,z3, x4,y4,z4)
        env.define("GetRayCollisionQuad", new NativeFunction(13, args
                -> GetRayCollisionQuad((Ray) args.get(0),
                        v3(args.get(1), args.get(2), args.get(3)),
                        v3(args.get(4), args.get(5), args.get(6)),
                        v3(args.get(7), args.get(8), args.get(9)),
                        v3(args.get(10), args.get(11), args.get(12)))));
    }

    /**
     * Registers timing, random, math helpers, world/screen projection, and
     * color utilities.
     *
     * <p>
     * <b>Timing &amp; random</b>: {@code GetFPS() -> Number}, {@code WaitTime(seconds)},
     * {@code SetRandomSeed(seed)}, {@code GetRandomValue(min, max) -> Number}
     *
     * <p>
     * <b>Math helpers</b> (implemented in Java):
     * {@code Clamp(value, min, max) -> Number},
     * {@code Lerp(start, end, t) -> Number}
     *
     * <p>
     * <b>Projection</b> (return {@code Vector2} — read with
     * {@code Vector2X/Y}):
     * {@code GetWorldToScreen(px, py, pz, camera) -> Vector2},
     * {@code GetWorldToScreen2D(px, py, camera2d) -> Vector2},
     * {@code GetScreenToWorld2D(px, py, camera2d) -> Vector2}
     *
     * <p>
     * <b>Color utilities</b>: {@code ColorToInt(color) -> Number} — packed RGBA
     * int, {@code ColorAlpha(color, alpha) -> Color} — returns color with new
     * alpha, {@code Fade(color, alpha) -> Color} — same as ColorAlpha (legacy),
     * {@code GetColor(hexValue) -> Color} — e.g. {@code GetColor(0xFF00FFFF)}
     */
    private void loadMath(Environment env) {
        // FPS and timing
        env.define("GetFPS", new NativeFunction(0, args -> (double) GetFPS()));
        env.define("WaitTime", new NativeFunction(1, args -> {
            WaitTime(toDouble(args.get(0)));
            return null;
        }));

        // Random
        env.define("SetRandomSeed", new NativeFunction(1, args -> {
            SetRandomSeed(toInt(args.get(0)));
            return null;
        }));
        env.define("GetRandomValue", new NativeFunction(2, args
                -> (double) GetRandomValue(toInt(args.get(0)), toInt(args.get(1)))));

        // Math helpers (implemented in Java for reliability)
        env.define("Clamp", new NativeFunction(3, args -> {
            double val = toDouble(args.get(0));
            double min = toDouble(args.get(1));
            double max = toDouble(args.get(2));
            return Math.max(min, Math.min(max, val));
        }));
        env.define("Lerp", new NativeFunction(3, args -> {
            double start = toDouble(args.get(0));
            double end = toDouble(args.get(1));
            double t = toDouble(args.get(2));
            return start + t * (end - start);
        }));

        // Projection utilities — return Vector2 (use Vector2X/Y to read)
        // GetWorldToScreen(px,py,pz, camera)
        env.define("GetWorldToScreen", new NativeFunction(4, args
                -> GetWorldToScreen(v3(args.get(0), args.get(1), args.get(2)),
                        (Camera3D) args.get(3))));
        // GetWorldToScreen2D(px,py, camera2d)
        env.define("GetWorldToScreen2D", new NativeFunction(3, args
                -> GetWorldToScreen2D(v2(args.get(0), args.get(1)), (Camera2D) args.get(2))));
        // GetScreenToWorld2D(px,py, camera2d)
        env.define("GetScreenToWorld2D", new NativeFunction(3, args
                -> GetScreenToWorld2D(v2(args.get(0), args.get(1)), (Camera2D) args.get(2))));

        // Color utilities
        env.define("ColorToInt", new NativeFunction(1, args
                -> (double) ColorToInt(clr(args.get(0)))));
        env.define("ColorAlpha", new NativeFunction(2, args
                -> ColorAlpha(clr(args.get(0)), toFloat(args.get(1)))));
        env.define("Fade", new NativeFunction(2, args
                -> Fade(clr(args.get(0)), toFloat(args.get(1)))));
        env.define("GetColor", new NativeFunction(1, args
                -> GetColor(toInt(args.get(0)))));
    }

    /**
     * Registers miscellaneous utility functions.
     *
     * <ul>
     * <li>{@code SetTraceLogLevel(level)} — use a {@code LOG_*} constant to
     * filter log output</li>
     * <li>{@code GetClipboardText() -> String}</li>
     * <li>{@code SetClipboardText(text)}</li>
     * <li>{@code PollInputEvents()} — process events without rendering a
     * frame</li>
     * <li>{@code SwapScreenBuffer()} — swap front/back buffers manually
     * (advanced)</li>
     * </ul>
     */
    private void loadUtility(Environment env) {
        env.define("SetTraceLogLevel", new NativeFunction(1, args -> {
            SetTraceLogLevel(toInt(args.get(0)));
            return null;
        }));
        env.define("GetClipboardText", new NativeFunction(0, args
                -> bpStr(GetClipboardText())));
        env.define("SetClipboardText", new NativeFunction(1, args -> {
            SetClipboardText(String.valueOf(args.get(0)));
            return null;
        }));
        env.define("PollInputEvents", new NativeFunction(0, args -> {
            PollInputEvents();
            return null;
        }));
        env.define("SwapScreenBuffer", new NativeFunction(0, args -> {
            SwapScreenBuffer();
            return null;
        }));
    }
}
