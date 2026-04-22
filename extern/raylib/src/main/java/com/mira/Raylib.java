package com.mira;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.functions.ReflectiveBinder;
import com.mira.runtime.interpreter.Environment;
import com.raylib.Raylib.BoundingBox;
import static com.raylib.Raylib.CAMERA_PERSPECTIVE;
import com.raylib.Raylib.Camera2D;
import com.raylib.Raylib.Camera3D;
import com.raylib.Raylib.Color;
import com.raylib.Raylib.Font;
import com.raylib.Raylib.Image;
import static com.raylib.Raylib.LoadShader;
import static com.raylib.Raylib.LoadShaderFromMemory;
import com.raylib.Raylib.Ray;
import com.raylib.Raylib.RayCollision;
import com.raylib.Raylib.Rectangle;
import com.raylib.Raylib.RenderTexture;
import static com.raylib.Raylib.SetShaderValue;
import com.raylib.Raylib.Shader;
import com.raylib.Raylib.Texture;
import com.raylib.Raylib.Vector2;
import com.raylib.Raylib.Vector3;

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

    private static String shaderPath(Object arg) {
        if (arg == null) {
            return null;
        }
        String s = String.valueOf(arg);
        return s.equals("null") ? null : s;
    }

    @Override
    public void loadLib(Environment environment) {
        loadManual(environment);
        ReflectiveBinder.bindConstants(com.raylib.Colors.class, environment);
        ReflectiveBinder.bindConstants(com.raylib.Raylib.class, environment);
        ReflectiveBinder.bindMethods(com.raylib.Raylib.class, environment);
    }

    private void loadManual(Environment env) {
        // Struct constructors — not static methods in Jaylib, so can't be auto-bound
        env.define("Color", new NativeFunction(4, args
                -> new Color().r((byte) toInt(args.get(0))).g((byte) toInt(args.get(1)))
                        .b((byte) toInt(args.get(2))).a((byte) toInt(args.get(3)))));
        env.define("Vector2", new NativeFunction(2, args
                -> v2(args.get(0), args.get(1))));
        env.define("Vector3", new NativeFunction(3, args
                -> v3(args.get(0), args.get(1), args.get(2))));
        env.define("Rectangle", new NativeFunction(4, args
                -> new Rectangle().x(toFloat(args.get(0))).y(toFloat(args.get(1)))
                        .width(toFloat(args.get(2))).height(toFloat(args.get(3)))));
        env.define("Ray", new NativeFunction(6, args
                -> new Ray()._position(v3(args.get(0), args.get(1), args.get(2)))
                        .direction(v3(args.get(3), args.get(4), args.get(5)))));
        env.define("BoundingBox", new NativeFunction(6, args
                -> new BoundingBox().min(v3(args.get(0), args.get(1), args.get(2)))
                        .max(v3(args.get(3), args.get(4), args.get(5)))));
        env.define("Camera3D", new NativeFunction(7, args
                -> new Camera3D()._position(v3(args.get(0), args.get(1), args.get(2)))
                        .target(v3(args.get(3), args.get(4), args.get(5)))
                        .up(new Vector3().x(0).y(1).z(0))
                        .fovy(toFloat(args.get(6)))
                        .projection(CAMERA_PERSPECTIVE)));
        env.define("Camera2D", new NativeFunction(6, args
                -> new Camera2D().offset(v2(args.get(0), args.get(1)))
                        .target(v2(args.get(2), args.get(3)))
                        .rotation(toFloat(args.get(4)))
                        .zoom(toFloat(args.get(5)))));

        // Field accessors — instance methods on opaque struct handles
        env.define("Vector2X", new NativeFunction(1, args -> (double) ((Vector2) args.get(0)).x()));
        env.define("Vector2Y", new NativeFunction(1, args -> (double) ((Vector2) args.get(0)).y()));
        env.define("Vector3X", new NativeFunction(1, args -> (double) ((Vector3) args.get(0)).x()));
        env.define("Vector3Y", new NativeFunction(1, args -> (double) ((Vector3) args.get(0)).y()));
        env.define("Vector3Z", new NativeFunction(1, args -> (double) ((Vector3) args.get(0)).z()));
        env.define("RectX", new NativeFunction(1, args -> (double) ((Rectangle) args.get(0)).x()));
        env.define("RectY", new NativeFunction(1, args -> (double) ((Rectangle) args.get(0)).y()));
        env.define("RectWidth", new NativeFunction(1, args -> (double) ((Rectangle) args.get(0)).width()));
        env.define("RectHeight", new NativeFunction(1, args -> (double) ((Rectangle) args.get(0)).height()));
        env.define("RayCollisionHit", new NativeFunction(1, args -> ((RayCollision) args.get(0)).hit()));
        env.define("RayCollisionDistance", new NativeFunction(1, args -> (double) ((RayCollision) args.get(0)).distance()));
        env.define("RayCollisionPoint", new NativeFunction(1, args -> ((RayCollision) args.get(0)).point()));
        env.define("ImageWidth", new NativeFunction(1, args -> (double) ((Image) args.get(0)).width()));
        env.define("ImageHeight", new NativeFunction(1, args -> (double) ((Image) args.get(0)).height()));
        env.define("TextureWidth", new NativeFunction(1, args -> (double) ((Texture) args.get(0)).width()));
        env.define("TextureHeight", new NativeFunction(1, args -> (double) ((Texture) args.get(0)).height()));
        env.define("GetRenderTextureTexture", new NativeFunction(1, args -> ((RenderTexture) args.get(0)).texture()));
        env.define("FontBaseSize", new NativeFunction(1, args -> (double) ((Font) args.get(0)).baseSize()));

        // Shader loading — override auto-bound versions to handle null shader paths
        env.define("LoadShader", new NativeFunction(2, args
                -> LoadShader(shaderPath(args.get(0)), shaderPath(args.get(1)))));
        env.define("LoadShaderFromMemory", new NativeFunction(2, args
                -> LoadShaderFromMemory(shaderPath(args.get(0)), shaderPath(args.get(1)))));

        // Shader value setters — require native pointer construction
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

        // Java-only math helpers — no Jaylib equivalent
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

        // Short aliases for mouse buttons (MOUSE_BUTTON_* are auto-bound from Jaylib)
        env.define("MOUSE_LEFT", 0.0);
        env.define("MOUSE_RIGHT", 1.0);
        env.define("MOUSE_MIDDLE", 2.0);
    }
}
