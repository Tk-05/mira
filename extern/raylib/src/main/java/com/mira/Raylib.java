package com.mira;

import static com.mira.lib.NativeOverride.custom;
import static com.mira.lib.NativeType.ANY;
import static com.mira.lib.NativeType.BOOL;
import static com.mira.lib.NativeType.NUMBER;
import static com.mira.lib.NativeType.OBJECT;
import static com.mira.lib.NativeType.STRING;
import static com.mira.lib.NativeType.VOID;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;

import com.mira.lib.NativeOverride;
import com.mira.lib.ReflectiveLib;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.raylib.Raylib.BoundingBox;
import static com.raylib.Raylib.CAMERA_ORTHOGRAPHIC;
import static com.raylib.Raylib.CAMERA_PERSPECTIVE;
import com.raylib.Raylib.Camera2D;
import com.raylib.Raylib.Camera3D;
import com.raylib.Raylib.Color;
import static com.raylib.Raylib.DrawMeshInstanced;
import com.raylib.Raylib.Font;
import com.raylib.Raylib.Image;
import static com.raylib.Raylib.LoadShader;
import static com.raylib.Raylib.LoadShaderFromMemory;
import com.raylib.Raylib.Material;
import com.raylib.Raylib.Matrix;
import com.raylib.Raylib.Model;
import com.raylib.Raylib.ModelAnimation;
import com.raylib.Raylib.Ray;
import com.raylib.Raylib.RayCollision;
import com.raylib.Raylib.Rectangle;
import com.raylib.Raylib.RenderTexture;
import static com.raylib.Raylib.RL_ATTACHMENT_DEPTH;
import static com.raylib.Raylib.RL_ATTACHMENT_TEXTURE2D;
import static com.raylib.Raylib.SetMaterialTexture;
import static com.raylib.Raylib.SetShaderValue;
import com.raylib.Raylib.Shader;
import com.raylib.Raylib.Texture;
import com.raylib.Raylib.Vector2;
import com.raylib.Raylib.Vector3;
import static com.raylib.Raylib.rlDisableFramebuffer;
import static com.raylib.Raylib.rlEnableFramebuffer;
import static com.raylib.Raylib.rlFramebufferAttach;
import static com.raylib.Raylib.rlLoadFramebuffer;
import static com.raylib.Raylib.rlLoadTextureDepth;
import static com.raylib.Raylib.rlUnloadFramebuffer;

/**
 * The bulk of Jaylib's static methods/constants are auto-bound reflectively
 * from {@link #targets()} - zero glue code needed for e.g. {@code InitWindow}
 * or {@code DrawRectangle}. {@link #overrides()} covers exactly what reflection
 * can't: struct constructors (instance methods in Jaylib, not static), field
 * accessors on opaque native struct handles, functions needing native pointer
 * construction, ambiguous overloads, and pure Java-side helpers with no raylib
 * equivalent.
 */
public class Raylib implements ReflectiveLib {

    private final AtomicBoolean stopping = new AtomicBoolean(false);
    // Side channel for LoadModelAnimationsRaw -> GetLastAnimationCount: a one-time
    // setup call pair (never per-frame), so a plain field is safe in practice.
    private int lastAnimationCount = 0;

    @Override
    public void interrupt() {
        stopping.set(true);
    }

    @Override
    public List<Class<?>> targets() {
        return List.of(com.raylib.Colors.class, com.raylib.Raylib.class);
    }

    @Override
    public java.util.Map<String, Object> constants() {
        // Short aliases for mouse buttons (MOUSE_BUTTON_* are auto-bound from Jaylib)
        return java.util.Map.of("MOUSE_LEFT", 0.0, "MOUSE_RIGHT", 1.0, "MOUSE_MIDDLE", 2.0);
    }

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
    public List<NativeOverride> overrides() {
        return List.of(
                // Struct constructors — not static methods in Jaylib, so can't be auto-bound
                custom("Color", List.of(NUMBER, NUMBER, NUMBER, NUMBER), OBJECT, args
                        -> new Color().r((byte) toInt(args.get(0))).g((byte) toInt(args.get(1)))
                        .b((byte) toInt(args.get(2))).a((byte) toInt(args.get(3)))),
                custom("Vector2", List.of(NUMBER, NUMBER), OBJECT, args
                        -> v2(args.get(0), args.get(1))),
                custom("Vector3", List.of(NUMBER, NUMBER, NUMBER), OBJECT, args
                        -> v3(args.get(0), args.get(1), args.get(2))),
                custom("Rectangle", List.of(NUMBER, NUMBER, NUMBER, NUMBER), OBJECT, args
                        -> new Rectangle().x(toFloat(args.get(0))).y(toFloat(args.get(1)))
                        .width(toFloat(args.get(2))).height(toFloat(args.get(3)))),
                custom("Ray", List.of(NUMBER, NUMBER, NUMBER, NUMBER, NUMBER, NUMBER), OBJECT, args
                        -> new Ray()._position(v3(args.get(0), args.get(1), args.get(2)))
                        .direction(v3(args.get(3), args.get(4), args.get(5)))),
                custom("BoundingBox", List.of(NUMBER, NUMBER, NUMBER, NUMBER, NUMBER, NUMBER), OBJECT, args
                        -> new BoundingBox().min(v3(args.get(0), args.get(1), args.get(2)))
                        .max(v3(args.get(3), args.get(4), args.get(5)))),
                custom("Camera3D", List.of(NUMBER, NUMBER, NUMBER, NUMBER, NUMBER, NUMBER, NUMBER), OBJECT, args
                        -> new Camera3D()._position(v3(args.get(0), args.get(1), args.get(2)))
                        .target(v3(args.get(3), args.get(4), args.get(5)))
                        .up(new Vector3().x(0).y(1).z(0))
                        .fovy(toFloat(args.get(6)))
                        .projection(CAMERA_PERSPECTIVE)),
                custom("Camera2D", List.of(NUMBER, NUMBER, NUMBER, NUMBER, NUMBER, NUMBER), OBJECT, args
                        -> new Camera2D().offset(v2(args.get(0), args.get(1)))
                        .target(v2(args.get(2), args.get(3)))
                        .rotation(toFloat(args.get(4)))
                        .zoom(toFloat(args.get(5)))),
                // Field accessors — instance methods on opaque struct handles
                custom("Vector2X", List.of(OBJECT), NUMBER, args -> (double) ((Vector2) args.get(0)).x()),
                custom("Vector2Y", List.of(OBJECT), NUMBER, args -> (double) ((Vector2) args.get(0)).y()),
                custom("Vector3X", List.of(OBJECT), NUMBER, args -> (double) ((Vector3) args.get(0)).x()),
                custom("Vector3Y", List.of(OBJECT), NUMBER, args -> (double) ((Vector3) args.get(0)).y()),
                custom("Vector3Z", List.of(OBJECT), NUMBER, args -> (double) ((Vector3) args.get(0)).z()),
                custom("RectX", List.of(OBJECT), NUMBER, args -> (double) ((Rectangle) args.get(0)).x()),
                custom("RectY", List.of(OBJECT), NUMBER, args -> (double) ((Rectangle) args.get(0)).y()),
                custom("RectWidth", List.of(OBJECT), NUMBER, args -> (double) ((Rectangle) args.get(0)).width()),
                custom("RectHeight", List.of(OBJECT), NUMBER, args -> (double) ((Rectangle) args.get(0)).height()),
                custom("RayCollisionHit", List.of(OBJECT), BOOL, args -> ((RayCollision) args.get(0)).hit()),
                custom("RayCollisionDistance", List.of(OBJECT), NUMBER,
                        args -> (double) ((RayCollision) args.get(0)).distance()),
                custom("RayCollisionPoint", List.of(OBJECT), OBJECT, args -> ((RayCollision) args.get(0)).point()),
                custom("ImageWidth", List.of(OBJECT), NUMBER, args -> (double) ((Image) args.get(0)).width()),
                custom("ImageHeight", List.of(OBJECT), NUMBER, args -> (double) ((Image) args.get(0)).height()),
                custom("TextureWidth", List.of(OBJECT), NUMBER, args -> (double) ((Texture) args.get(0)).width()),
                custom("TextureHeight", List.of(OBJECT), NUMBER, args -> (double) ((Texture) args.get(0)).height()),
                custom("GetRenderTextureTexture", List.of(OBJECT), OBJECT,
                        args -> ((RenderTexture) args.get(0)).texture()),
                custom("FontBaseSize", List.of(OBJECT), NUMBER, args -> (double) ((Font) args.get(0)).baseSize()),
                // Shader loading — override auto-bound versions to handle null shader paths
                custom("LoadShader", List.of(STRING, STRING), OBJECT, args
                        -> LoadShader(shaderPath(args.get(0)), shaderPath(args.get(1)))),
                custom("LoadShaderFromMemory", List.of(STRING, STRING), OBJECT, args
                        -> LoadShaderFromMemory(shaderPath(args.get(0)), shaderPath(args.get(1)))),
                // Shader value setters — require native pointer construction
                custom("SetShaderValueInt", List.of(OBJECT, NUMBER, NUMBER), VOID, args -> {
                    try (IntPointer p = new IntPointer(1).put(toInt(args.get(2)))) {
                        SetShaderValue((Shader) args.get(0), toInt(args.get(1)), p, 4);
                    }
                    return null;
                }),
                custom("SetShaderValueFloat", List.of(OBJECT, NUMBER, NUMBER), VOID, args -> {
                    try (FloatPointer p = new FloatPointer(1).put(toFloat(args.get(2)))) {
                        SetShaderValue((Shader) args.get(0), toInt(args.get(1)), p, 0);
                    }
                    return null;
                }),
                custom("SetShaderValueVec2", List.of(OBJECT, NUMBER, NUMBER, NUMBER), VOID, args -> {
                    try (FloatPointer p = new FloatPointer(2)) {
                        p.put(0, toFloat(args.get(2)));
                        p.put(1, toFloat(args.get(3)));
                        SetShaderValue((Shader) args.get(0), toInt(args.get(1)), p, 1);
                    }
                    return null;
                }),
                custom("SetShaderValueVec3", List.of(OBJECT, NUMBER, NUMBER, NUMBER, NUMBER), VOID, args -> {
                    try (FloatPointer p = new FloatPointer(3)) {
                        p.put(0, toFloat(args.get(2)));
                        p.put(1, toFloat(args.get(3)));
                        p.put(2, toFloat(args.get(4)));
                        SetShaderValue((Shader) args.get(0), toInt(args.get(1)), p, 2);
                    }
                    return null;
                }),
                custom("SetShaderValueVec4", List.of(OBJECT, NUMBER, NUMBER, NUMBER, NUMBER, NUMBER), VOID, args -> {
                    try (FloatPointer p = new FloatPointer(4)) {
                        p.put(0, toFloat(args.get(2)));
                        p.put(1, toFloat(args.get(3)));
                        p.put(2, toFloat(args.get(4)));
                        p.put(3, toFloat(args.get(5)));
                        SetShaderValue((Shader) args.get(0), toInt(args.get(1)), p, 3);
                    }
                    return null;
                }),
                // Model transform / material — instance-field mutators, not reflectively auto-bindable
                custom("SetModelTransform", List.of(OBJECT, OBJECT), VOID, args -> {
                    ((Model) args.get(0)).transform((Matrix) args.get(1));
                    return null;
                }),
                // DrawMesh/DrawModel read the shader straight off the material — unlike
                // 2D immediate-mode drawing, BeginShaderMode/EndShaderMode has no effect
                // on them, so a custom 3D shader has to be assigned here instead.
                custom("SetModelShader", List.of(OBJECT, OBJECT), VOID, args -> {
                    ((Model) args.get(0)).materials().shader((Shader) args.get(1));
                    return null;
                }),
                custom("SetModelMaterialTexture", List.of(OBJECT, NUMBER, NUMBER, OBJECT), VOID, args -> {
                    Model model = (Model) args.get(0);
                    Material material = model.materials().getPointer(toInt(args.get(1)));
                    SetMaterialTexture(material, toInt(args.get(2)), (Texture) args.get(3));
                    return null;
                }),
                custom("BoundingBoxMin", List.of(OBJECT), OBJECT, args -> ((BoundingBox) args.get(0)).min()),
                custom("BoundingBoxMax", List.of(OBJECT), OBJECT, args -> ((BoundingBox) args.get(0)).max()),
                // Shadow mapping — raylib has no public LoadRenderTexture variant for a
                // depth-only attachment, so this replicates raylib's own official
                // shadowmap example (rlgl low-level framebuffer calls) directly.
                custom("LoadShadowmapRenderTexture", List.of(NUMBER, NUMBER), OBJECT, args -> {
                    int width = toInt(args.get(0));
                    int height = toInt(args.get(1));
                    RenderTexture target = new RenderTexture();
                    int fboId = rlLoadFramebuffer();
                    target.id(fboId);
                    target.texture(new Texture().width(width).height(height));
                    if (fboId > 0) {
                        rlEnableFramebuffer(fboId);
                        int depthId = rlLoadTextureDepth(width, height, false);
                        target.depth(new Texture().id(depthId).width(width).height(height).mipmaps(1).format(19));
                        rlFramebufferAttach(fboId, depthId, RL_ATTACHMENT_DEPTH, RL_ATTACHMENT_TEXTURE2D, 0);
                        rlDisableFramebuffer();
                    }
                    return target;
                }),
                custom("UnloadShadowmapRenderTexture", List.of(OBJECT), VOID, args -> {
                    rlUnloadFramebuffer(((RenderTexture) args.get(0)).id());
                    return null;
                }),
                custom("GetRenderTextureDepthTexture", List.of(OBJECT), OBJECT,
                        args -> ((RenderTexture) args.get(0)).depth()),
                // An orthographic Camera3D, for rendering a directional light's shadow
                // pass — the regular Camera3D constructor always hardcodes perspective.
                custom("OrthoCamera3D", List.of(NUMBER, NUMBER, NUMBER, NUMBER, NUMBER, NUMBER, NUMBER), OBJECT, args
                        -> new Camera3D()._position(v3(args.get(0), args.get(1), args.get(2)))
                        .target(v3(args.get(3), args.get(4), args.get(5)))
                        .up(new Vector3().x(0).y(1).z(0))
                        .fovy(toFloat(args.get(6)))
                        .projection(CAMERA_ORTHOGRAPHIC)),
                // Skeletal animation — LoadModelAnimations has ambiguous overloads once
                // reflectively scanned (String/BytePointer x IntPointer/IntBuffer/int[]
                // all score equally under the auto-scanner's heuristic), so it's bound
                // manually against one explicit overload instead. The resulting count
                // is stashed for the immediately-following GetLastAnimationCount call —
                // safe because loading is a one-time setup step, never called per-frame
                // or concurrently.
                custom("LoadModelAnimationsRaw", List.of(STRING), OBJECT, args -> {
                    String path = String.valueOf(args.get(0));
                    int[] animCount = new int[1];
                    ModelAnimation anims = com.raylib.Raylib.LoadModelAnimations(path, animCount);
                    lastAnimationCount = animCount[0];
                    return anims;
                }),
                custom("GetLastAnimationCount", List.of(), NUMBER, args -> (double) lastAnimationCount),
                custom("GetAnimationAt", List.of(OBJECT, NUMBER), OBJECT,
                        args -> ((ModelAnimation) args.get(0)).getPointer(toInt(args.get(1)))),
                custom("AnimationFrameCount", List.of(OBJECT), NUMBER,
                        args -> (double) ((ModelAnimation) args.get(0)).frameCount()),
                // GPU instancing: DrawMeshInstanced needs one contiguous native Matrix
                // array, which Mira code can't build directly — Mira instead builds each
                // instance's Matrix individually (MatrixTranslate/RotateXYZ/Scale/Multiply,
                // already reflectively bound) and collects them into a plain list; this
                // copies that list's Matrix values field-by-field into a real array. Draws
                // every instance with the model's first mesh/material (unlit, no
                // per-instance lighting) — fine for background-decoration-style use.
                custom("DrawMeshInstancedRaw", List.of(OBJECT, ANY), VOID, "model, matrixList", args -> {
                    Model model = (Model) args.get(0);
                    if (!(args.get(1) instanceof ListExpression list)) {
                        throw new RuntimeException("DrawMeshInstancedRaw requires a list of matrices");
                    }
                    List<Expression> members = list.getMembers();
                    int count = members.size();
                    if (count == 0) {
                        return null;
                    }
                    try (Matrix transforms = new Matrix(count)) {
                        for (int i = 0; i < count; i++) {
                            Matrix src = (Matrix) members.get(i).accept(null);
                            transforms.position(i)
                                    .m0(src.m0()).m1(src.m1()).m2(src.m2()).m3(src.m3())
                                    .m4(src.m4()).m5(src.m5()).m6(src.m6()).m7(src.m7())
                                    .m8(src.m8()).m9(src.m9()).m10(src.m10()).m11(src.m11())
                                    .m12(src.m12()).m13(src.m13()).m14(src.m14()).m15(src.m15());
                        }
                        transforms.position(0);
                        DrawMeshInstanced(model.meshes(), model.materials(), transforms, count);
                    }
                    return null;
                }),
                // Java-only math helpers — no Jaylib equivalent
                custom("Clamp", List.of(NUMBER, NUMBER, NUMBER), NUMBER, args -> {
                    double val = toDouble(args.get(0));
                    double min = toDouble(args.get(1));
                    double max = toDouble(args.get(2));
                    return Math.max(min, Math.min(max, val));
                }),
                custom("Lerp", List.of(NUMBER, NUMBER, NUMBER), NUMBER, args -> {
                    double start = toDouble(args.get(0));
                    double end = toDouble(args.get(1));
                    double t = toDouble(args.get(2));
                    return start + t * (end - start);
                }),
                // LoadFontEx with extended codepoints: ASCII (32–126) + bullet (8226) + middle dot (183)
                custom("LoadFontEx", List.of(STRING, NUMBER), OBJECT, args -> {
                    String path = String.valueOf(args.get(0));
                    int baseSize = toInt(args.get(1));
                    int[] cp = new int[97]; // 95 ASCII + 2 extra
                    for (int i = 0; i < 95; i++) {
                        cp[i] = 32 + i;
                    }
                    cp[95] = 183;   // ·
                    cp[96] = 8226;  // •
                    try (IntPointer ptr = new IntPointer(cp.length)) {
                        for (int i = 0; i < cp.length; i++) {
                            ptr.put(i, cp[i]);
                        }
                        return com.raylib.Raylib.LoadFontEx(path, baseSize, ptr, cp.length);
                    }
                }),
                // MeasureTextEx — overrides auto-bound version to return width as double instead of Vector2
                custom("MeasureTextEx", List.of(OBJECT, STRING, NUMBER, NUMBER), NUMBER, args -> {
                    Font font = (Font) args.get(0);
                    String text = String.valueOf(args.get(1));
                    float fontSize = toFloat(args.get(2));
                    float spacing = toFloat(args.get(3));
                    return (double) com.raylib.Raylib.MeasureTextEx(font, text, fontSize, spacing).x();
                }),
                custom("WindowShouldClose", List.of(), BOOL, args
                        -> stopping.get() || com.raylib.Raylib.WindowShouldClose()));
    }
}
