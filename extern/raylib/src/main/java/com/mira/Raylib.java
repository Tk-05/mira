package com.mira;

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
import static com.raylib.Raylib.BeginDrawing;
import static com.raylib.Raylib.BeginMode2D;
import static com.raylib.Raylib.BeginMode3D;
import static com.raylib.Raylib.CAMERA_PERSPECTIVE;
import com.raylib.Raylib.Camera2D;
import com.raylib.Raylib.Camera3D;
import static com.raylib.Raylib.ClearBackground;
import static com.raylib.Raylib.CloseAudioDevice;
import static com.raylib.Raylib.CloseWindow;
import com.raylib.Raylib.Color;
import static com.raylib.Raylib.DrawCircle;
import static com.raylib.Raylib.DrawCircleLines;
import static com.raylib.Raylib.DrawCube;
import static com.raylib.Raylib.DrawCubeWires;
import static com.raylib.Raylib.DrawEllipse;
import static com.raylib.Raylib.DrawFPS;
import static com.raylib.Raylib.DrawGrid;
import static com.raylib.Raylib.DrawLine;
import static com.raylib.Raylib.DrawLineEx;
import static com.raylib.Raylib.DrawPixel;
import static com.raylib.Raylib.DrawRectangle;
import static com.raylib.Raylib.DrawRectangleLines;
import static com.raylib.Raylib.DrawSphere;
import static com.raylib.Raylib.DrawSphereWires;
import static com.raylib.Raylib.DrawText;
import static com.raylib.Raylib.DrawTexture;
import static com.raylib.Raylib.DrawTriangle;
import static com.raylib.Raylib.EndDrawing;
import static com.raylib.Raylib.EndMode2D;
import static com.raylib.Raylib.EndMode3D;
import static com.raylib.Raylib.GetFrameTime;
import static com.raylib.Raylib.GetKeyPressed;
import static com.raylib.Raylib.GetMouseWheelMove;
import static com.raylib.Raylib.GetMouseX;
import static com.raylib.Raylib.GetMouseY;
import static com.raylib.Raylib.GetScreenHeight;
import static com.raylib.Raylib.GetScreenWidth;
import static com.raylib.Raylib.GetTime;
import static com.raylib.Raylib.InitAudioDevice;
import static com.raylib.Raylib.InitWindow;
import static com.raylib.Raylib.IsKeyDown;
import static com.raylib.Raylib.IsKeyPressed;
import static com.raylib.Raylib.IsKeyReleased;
import static com.raylib.Raylib.IsKeyUp;
import static com.raylib.Raylib.IsMouseButtonDown;
import static com.raylib.Raylib.IsMouseButtonPressed;
import static com.raylib.Raylib.IsMouseButtonReleased;
import static com.raylib.Raylib.IsWindowFullscreen;
import static com.raylib.Raylib.LoadSound;
import static com.raylib.Raylib.LoadTexture;
import static com.raylib.Raylib.MeasureText;
import static com.raylib.Raylib.PlaySound;
import static com.raylib.Raylib.SetMousePosition;
import static com.raylib.Raylib.SetSoundVolume;
import static com.raylib.Raylib.SetTargetFPS;
import static com.raylib.Raylib.SetWindowTitle;
import com.raylib.Raylib.Sound;
import com.raylib.Raylib.Texture;
import static com.raylib.Raylib.ToggleFullscreen;
import static com.raylib.Raylib.UnloadSound;
import static com.raylib.Raylib.UnloadTexture;
import static com.raylib.Raylib.UpdateCamera;
import com.raylib.Raylib.Vector2;
import com.raylib.Raylib.Vector3;
import static com.raylib.Raylib.WindowShouldClose;

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

    @Override
    public void loadLib(Environment environment) {
        environment.define("Color", new NativeFunction(4, args -> {
            return new Color()
                    .r((byte) toInt(args.get(0)))
                    .g((byte) toInt(args.get(1)))
                    .b((byte) toInt(args.get(2)))
                    .a((byte) toInt(args.get(3)));
        }));

        environment.define("BLACK", BLACK);
        environment.define("WHITE", WHITE);
        environment.define("RED", RED);
        environment.define("GREEN", GREEN);
        environment.define("BLUE", BLUE);
        environment.define("YELLOW", YELLOW);
        environment.define("ORANGE", ORANGE);
        environment.define("PURPLE", PURPLE);
        environment.define("PINK", PINK);
        environment.define("MAROON", MAROON);
        environment.define("GOLD", GOLD);
        environment.define("LIME", LIME);
        environment.define("SKYBLUE", SKYBLUE);
        environment.define("VIOLET", VIOLET);
        environment.define("DARKBLUE", DARKBLUE);
        environment.define("DARKGREEN", DARKGREEN);
        environment.define("DARKBROWN", DARKBROWN);
        environment.define("DARKGRAY", DARKGRAY);
        environment.define("GRAY", GRAY);
        environment.define("LIGHTGRAY", LIGHTGRAY);
        environment.define("BROWN", BROWN);
        environment.define("BEIGE", BEIGE);
        environment.define("MAGENTA", MAGENTA);
        environment.define("DARKPURPLE", DARKPURPLE);
        environment.define("RAYWHITE", RAYWHITE);

        environment.define("CAMERA_CUSTOM", 0.0);
        environment.define("CAMERA_FREE", 1.0);
        environment.define("CAMERA_ORBITAL", 2.0);
        environment.define("CAMERA_FIRST_PERSON", 3.0);
        environment.define("CAMERA_THIRD_PERSON", 4.0);

        environment.define("CAMERA_PERSPECTIVE", 0.0);
        environment.define("CAMERA_ORTHOGRAPHIC", 1.0);

        environment.define("KEY_SPACE", 32.0);
        environment.define("KEY_ENTER", 257.0);
        environment.define("KEY_ESCAPE", 256.0);
        environment.define("KEY_BACKSPACE", 259.0);
        environment.define("KEY_TAB", 258.0);
        environment.define("KEY_DELETE", 261.0);
        environment.define("KEY_UP", 265.0);
        environment.define("KEY_DOWN", 264.0);
        environment.define("KEY_LEFT", 263.0);
        environment.define("KEY_RIGHT", 262.0);
        environment.define("KEY_LEFT_SHIFT", 340.0);
        environment.define("KEY_LEFT_CTRL", 341.0);
        environment.define("KEY_LEFT_ALT", 342.0);
        environment.define("KEY_A", 65.0);
        environment.define("KEY_B", 66.0);
        environment.define("KEY_C", 67.0);
        environment.define("KEY_D", 68.0);
        environment.define("KEY_E", 69.0);
        environment.define("KEY_F", 70.0);
        environment.define("KEY_G", 71.0);
        environment.define("KEY_H", 72.0);
        environment.define("KEY_I", 73.0);
        environment.define("KEY_J", 74.0);
        environment.define("KEY_K", 75.0);
        environment.define("KEY_L", 76.0);
        environment.define("KEY_M", 77.0);
        environment.define("KEY_N", 78.0);
        environment.define("KEY_O", 79.0);
        environment.define("KEY_P", 80.0);
        environment.define("KEY_Q", 81.0);
        environment.define("KEY_R", 82.0);
        environment.define("KEY_S", 83.0);
        environment.define("KEY_T", 84.0);
        environment.define("KEY_U", 85.0);
        environment.define("KEY_V", 86.0);
        environment.define("KEY_W", 87.0);
        environment.define("KEY_X", 88.0);
        environment.define("KEY_Y", 89.0);
        environment.define("KEY_Z", 90.0);
        environment.define("KEY_0", 48.0);
        environment.define("KEY_1", 49.0);
        environment.define("KEY_2", 50.0);
        environment.define("KEY_3", 51.0);
        environment.define("KEY_4", 52.0);
        environment.define("KEY_5", 53.0);
        environment.define("KEY_6", 54.0);
        environment.define("KEY_7", 55.0);
        environment.define("KEY_8", 56.0);
        environment.define("KEY_9", 57.0);

        environment.define("MOUSE_LEFT", 0.0);
        environment.define("MOUSE_RIGHT", 1.0);
        environment.define("MOUSE_MIDDLE", 2.0);

        environment.define("InitWindow", new NativeFunction(3, args -> {
            InitWindow(toInt(args.get(0)), toInt(args.get(1)), String.valueOf(args.get(2)));
            return null;
        }));

        environment.define("CloseWindow", new NativeFunction(0, args -> {
            CloseWindow();
            return null;
        }));

        environment.define("WindowShouldClose", new NativeFunction(0, args -> WindowShouldClose()));

        environment.define("SetTargetFPS", new NativeFunction(1, args -> {
            SetTargetFPS(toInt(args.get(0)));
            return null;
        }));

        environment.define("GetScreenWidth", new NativeFunction(0, args -> (double) GetScreenWidth()));

        environment.define("GetScreenHeight", new NativeFunction(0, args -> (double) GetScreenHeight()));

        environment.define("SetWindowTitle", new NativeFunction(1, args -> {
            SetWindowTitle(String.valueOf(args.get(0)));
            return null;
        }));

        environment.define("ToggleFullscreen", new NativeFunction(0, args -> {
            ToggleFullscreen();
            return null;
        }));

        environment.define("IsWindowFullscreen", new NativeFunction(0, args -> IsWindowFullscreen()));

        environment.define("BeginDrawing", new NativeFunction(0, args -> {
            BeginDrawing();
            return null;
        }));

        environment.define("EndDrawing", new NativeFunction(0, args -> {
            EndDrawing();
            return null;
        }));

        environment.define("ClearBackground", new NativeFunction(1, args -> {
            ClearBackground((Color) args.get(0));
            return null;
        }));

        environment.define("DrawPixel", new NativeFunction(3, args -> {
            DrawPixel(toInt(args.get(0)), toInt(args.get(1)), (Color) args.get(2));
            return null;
        }));

        environment.define("DrawLine", new NativeFunction(5, args -> {
            DrawLine(toInt(args.get(0)), toInt(args.get(1)),
                    toInt(args.get(2)), toInt(args.get(3)),
                    (Color) args.get(4));
            return null;
        }));

        environment.define("DrawLineEx", new NativeFunction(6, args -> {
            Vector2 start = new Vector2().x(toFloat(args.get(0))).y(toFloat(args.get(1)));
            Vector2 end = new Vector2().x(toFloat(args.get(2))).y(toFloat(args.get(3)));
            DrawLineEx(start, end, toFloat(args.get(4)), (Color) args.get(5));
            return null;
        }));

        environment.define("DrawCircle", new NativeFunction(4, args -> {
            DrawCircle(toInt(args.get(0)), toInt(args.get(1)),
                    toFloat(args.get(2)), (Color) args.get(3));
            return null;
        }));

        environment.define("DrawCircleLines", new NativeFunction(4, args -> {
            DrawCircleLines(toInt(args.get(0)), toInt(args.get(1)),
                    toFloat(args.get(2)), (Color) args.get(3));
            return null;
        }));

        environment.define("DrawEllipse", new NativeFunction(5, args -> {
            DrawEllipse(toInt(args.get(0)), toInt(args.get(1)),
                    toFloat(args.get(2)), toFloat(args.get(3)),
                    (Color) args.get(4));
            return null;
        }));

        environment.define("DrawRectangle", new NativeFunction(5, args -> {
            DrawRectangle(toInt(args.get(0)), toInt(args.get(1)),
                    toInt(args.get(2)), toInt(args.get(3)),
                    (Color) args.get(4));
            return null;
        }));

        environment.define("DrawRectangleLines", new NativeFunction(5, args -> {
            DrawRectangleLines(toInt(args.get(0)), toInt(args.get(1)),
                    toInt(args.get(2)), toInt(args.get(3)),
                    (Color) args.get(4));
            return null;
        }));

        environment.define("DrawTriangle", new NativeFunction(7, args -> {
            Vector2 v1 = new Vector2().x(toFloat(args.get(0))).y(toFloat(args.get(1)));
            Vector2 v2 = new Vector2().x(toFloat(args.get(2))).y(toFloat(args.get(3)));
            Vector2 v3 = new Vector2().x(toFloat(args.get(4))).y(toFloat(args.get(5)));
            DrawTriangle(v1, v2, v3, (Color) args.get(6));
            return null;
        }));

        environment.define("DrawText", new NativeFunction(5, args -> {
            DrawText(String.valueOf(args.get(0)),
                    toInt(args.get(1)), toInt(args.get(2)),
                    toInt(args.get(3)), (Color) args.get(4));
            return null;
        }));

        environment.define("DrawFPS", new NativeFunction(2, args -> {
            DrawFPS(toInt(args.get(0)), toInt(args.get(1)));
            return null;
        }));

        environment.define("MeasureText", new NativeFunction(2, args
                -> (double) MeasureText(String.valueOf(args.get(0)), toInt(args.get(1)))));

        environment.define("LoadTexture", new NativeFunction(1, args
                -> LoadTexture(String.valueOf(args.get(0)))));

        environment.define("DrawTexture", new NativeFunction(4, args -> {
            DrawTexture((Texture) args.get(0),
                    toInt(args.get(1)), toInt(args.get(2)),
                    (Color) args.get(3));
            return null;
        }));

        environment.define("UnloadTexture", new NativeFunction(1, args -> {
            UnloadTexture((Texture) args.get(0));
            return null;
        }));

        environment.define("IsKeyPressed", new NativeFunction(1, args -> IsKeyPressed(toInt(args.get(0)))));

        environment.define("IsKeyDown", new NativeFunction(1, args -> IsKeyDown(toInt(args.get(0)))));

        environment.define("IsKeyReleased", new NativeFunction(1, args -> IsKeyReleased(toInt(args.get(0)))));

        environment.define("IsKeyUp", new NativeFunction(1, args -> IsKeyUp(toInt(args.get(0)))));

        environment.define("GetKeyPressed", new NativeFunction(0, args -> (double) GetKeyPressed()));

        environment.define("IsMouseButtonPressed", new NativeFunction(1, args -> IsMouseButtonPressed(toInt(args.get(0)))));

        environment.define("IsMouseButtonDown", new NativeFunction(1, args -> IsMouseButtonDown(toInt(args.get(0)))));

        environment.define("IsMouseButtonReleased", new NativeFunction(1, args -> IsMouseButtonReleased(toInt(args.get(0)))));

        environment.define("GetMouseX", new NativeFunction(0, args -> (double) GetMouseX()));

        environment.define("GetMouseY", new NativeFunction(0, args -> (double) GetMouseY()));

        environment.define("GetMouseWheelMove", new NativeFunction(0, args -> (double) GetMouseWheelMove()));

        environment.define("SetMousePosition", new NativeFunction(2, args -> {
            SetMousePosition(toInt(args.get(0)), toInt(args.get(1)));
            return null;
        }));

        environment.define("GetTime", new NativeFunction(0, args -> GetTime()));

        environment.define("GetFrameTime", new NativeFunction(0, args -> (double) GetFrameTime()));

        environment.define("Camera3D", new NativeFunction(7, args
                -> new Camera3D()
                        ._position(new Vector3()
                                .x(toFloat(args.get(0)))
                                .y(toFloat(args.get(1)))
                                .z(toFloat(args.get(2))))
                        .target(new Vector3()
                                .x(toFloat(args.get(3)))
                                .y(toFloat(args.get(4)))
                                .z(toFloat(args.get(5))))
                        .up(new Vector3().x(0).y(1).z(0))
                        .fovy(toFloat(args.get(6)))
                        .projection(CAMERA_PERSPECTIVE)));

        environment.define("BeginMode3D", new NativeFunction(1, args -> {
            BeginMode3D((Camera3D) args.get(0));
            return null;
        }));

        environment.define("EndMode3D", new NativeFunction(0, args -> {
            EndMode3D();
            return null;
        }));

        environment.define("UpdateCamera", new NativeFunction(2, args -> {
            UpdateCamera((Camera3D) args.get(0), toInt(args.get(1)));
            return null;
        }));

        environment.define("DrawGrid", new NativeFunction(2, args -> {
            DrawGrid(toInt(args.get(0)), toFloat(args.get(1)));
            return null;
        }));

        environment.define("DrawCube", new NativeFunction(7, args -> {
            Vector3 pos = new Vector3()
                    .x(toFloat(args.get(0)))
                    .y(toFloat(args.get(1)))
                    .z(toFloat(args.get(2)));
            DrawCube(pos, toFloat(args.get(3)), toFloat(args.get(4)),
                    toFloat(args.get(5)), (Color) args.get(6));
            return null;
        }));

        environment.define("DrawCubeWires", new NativeFunction(7, args -> {
            Vector3 pos = new Vector3()
                    .x(toFloat(args.get(0)))
                    .y(toFloat(args.get(1)))
                    .z(toFloat(args.get(2)));
            DrawCubeWires(pos, toFloat(args.get(3)), toFloat(args.get(4)),
                    toFloat(args.get(5)), (Color) args.get(6));
            return null;
        }));

        environment.define("DrawSphere", new NativeFunction(5, args -> {
            Vector3 pos = new Vector3()
                    .x(toFloat(args.get(0)))
                    .y(toFloat(args.get(1)))
                    .z(toFloat(args.get(2)));
            DrawSphere(pos, toFloat(args.get(3)), (Color) args.get(4));
            return null;
        }));

        environment.define("DrawSphereWires", new NativeFunction(7, args -> {
            Vector3 pos = new Vector3()
                    .x(toFloat(args.get(0)))
                    .y(toFloat(args.get(1)))
                    .z(toFloat(args.get(2)));
            DrawSphereWires(pos, toFloat(args.get(3)),
                    toInt(args.get(4)), toInt(args.get(5)),
                    (Color) args.get(6));
            return null;
        }));

        environment.define("Camera2D", new NativeFunction(6, args
                -> new Camera2D()
                        .offset(new Vector2()
                                .x(toFloat(args.get(0)))
                                .y(toFloat(args.get(1))))
                        .target(new Vector2()
                                .x(toFloat(args.get(2)))
                                .y(toFloat(args.get(3))))
                        .rotation(toFloat(args.get(4)))
                        .zoom(toFloat(args.get(5)))));

        environment.define("BeginMode2D", new NativeFunction(1, args -> {
            BeginMode2D((Camera2D) args.get(0));
            return null;
        }));

        environment.define("EndMode2D", new NativeFunction(0, args -> {
            EndMode2D();
            return null;
        }));

        environment.define("InitAudioDevice", new NativeFunction(0, args -> {
            InitAudioDevice();
            return null;
        }));

        environment.define("CloseAudioDevice", new NativeFunction(0, args -> {
            CloseAudioDevice();
            return null;
        }));

        environment.define("LoadSound", new NativeFunction(1, args
                -> LoadSound(String.valueOf(args.get(0)))));

        environment.define("PlaySound", new NativeFunction(1, args -> {
            PlaySound((Sound) args.get(0));
            return null;
        }));

        environment.define("UnloadSound", new NativeFunction(1, args -> {
            UnloadSound((Sound) args.get(0));
            return null;
        }));

        environment.define("SetSoundVolume", new NativeFunction(2, args -> {
            SetSoundVolume((Sound) args.get(0), toFloat(args.get(1)));
            return null;
        }));
    }
}
