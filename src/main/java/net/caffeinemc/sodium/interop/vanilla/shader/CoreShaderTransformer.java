package net.caffeinemc.sodium.interop.vanilla.shader;

import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.match.AutoHintedMatcher;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.ast.transform.EnumASTTransformer;
import net.caffeinemc.gfx.api.shader.ShaderType;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import org.antlr.v4.runtime.RecognitionException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoreShaderTransformer {

    private static final Pattern versionPattern = Pattern.compile("^.*#version\\s+(\\d+)", Pattern.DOTALL);
    private static final AutoHintedMatcher<ExternalDeclaration> uniformVec4EntityColor = new AutoHintedMatcher<>(
            "uniform vec4 entityColor;", io.github.douira.glsl_transformer.ast.query.match.Matcher.externalDeclarationPattern);

    private static final EnumASTTransformer<CoreShaderParameters, ShaderType> transformer;

    static {
        transformer = new EnumASTTransformer<CoreShaderParameters, ShaderType>(ShaderType.class) {
            @Override
            public TranslationUnit parseTranslationUnit(String input) throws RecognitionException {
                // parse #version directive using an efficient regex before parsing so that the
                // parser can be set to the correct version
                Matcher matcher = versionPattern.matcher(input);
                if (!matcher.find()) {
                    throw new IllegalArgumentException("No #version directive found in source code!");
                }
                transformer.getLexer().version = Version.GL45;

                return super.parseTranslationUnit(input);
            }
        };
        transformer.setTransformation((trees, parameters) -> {
            for (ShaderType type : ShaderType.values()) {
                TranslationUnit tree = trees.get(type);
                if (tree == null) {
                    continue;
                }
                Root root = tree.getRoot();

                Root.indexBuildSession(tree, () -> {
                    transform(transformer, tree, root, type, parameters);
                });
            }
        });

        transformer.setPrintType(PrintType.INDENTED);
    }

    public static void transform(
            ASTParser t,
            TranslationUnit tree,
            Root root,
            ShaderType type, CoreShaderParameters parameters) {
        tree.getVersionStatement().version = Version.GL45;
        root.replaceReferenceExpressions(t,"ProjMat", "mat_proj");
        root.replaceReferenceExpressions(t,"ModelViewMat", "mat_modelview");

        if (type == ShaderType.VERTEX) {
            // Alias of gl_MultiTexCoord1 on 1.15+ for OptiFine
            // See https://github.com/IrisShaders/Iris/issues/1149
            root.rename("gl_MultiTexCoord2", "gl_MultiTexCoord1");

            root.replaceReferenceExpressions(t, "UV0",
                        "_vert_tex_diffuse_coord");

            root.replaceReferenceExpressions(t, "UV2",
                        "_vert_tex_light_coord");

            root.replaceReferenceExpressions(t, "Color",
                    "_vert_color_shade");

            root.replaceReferenceExpressions(t, "Normal",
                    "_vert_normal");
        }

        root.replaceReferenceExpressions(t, "ColorModulator",
                "vec4(1)");

        root.replaceReferenceExpressions(t, "FogStart",
                "fog_start");

        root.replaceReferenceExpressions(t, "FogEnd",
                "fog_end");

        root.replaceReferenceExpressions(t, "FogColor",
                "fog_color");

        root.replaceReferenceExpressions(t, "FogShape",
                "fog_mode");

        root.replaceReferenceExpressions(t, "TextureMat",
                "mat4(1.0)");

        root.replaceReferenceExpressions(t, "MINECRAFT_LIGHT_POWER", "0.6");
        root.replaceReferenceExpressions(t, "MINECRAFT_AMBIENT_LIGHT", "0.4");

        if (type == ShaderType.VERTEX) {

            // TODO: Vaporwave-Shaderpack expects that vertex positions will be aligned to
            // chunks.
            root.replaceReferenceExpressions(t, "Position", "_vert_position");
            root.replaceReferenceExpressions(t, "ChunkOffset", "transforms[" + (parameters.baseInstanced ? "gl_BaseInstanceARB" : "gl_DrawIDARB") + "].translation");

            tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_FUNCTIONS,
                    // translated from sodium's chunk_vertex.glsl
                    "layout(std140, binding = 0) uniform CameraMatrices {" +
                    "    mat4 mat_proj;" +
                    "" +
                    "    mat4 mat_modelview;" +
                    "" +
                    "    mat4 mat_modelviewproj;" +
                    "};",
                    
                    "layout(std140, binding = 2) uniform FogParameters {" +
                    "    vec4 fog_color;" +
                    "" +
                    "    float fog_start;" +
                    "" +
                    "    float fog_end;" +
                    "" +
                    "    int fog_mode;" +
                    "};",

                    "const vec3[6] normalLookup = vec3[](" +
                    "    vec3(0, -1, 0)," +
                    "    vec3(0, 1, 0)," +
                    "    vec3(0, 0, -1)," +
                    "    vec3(0, 0, 1)," +
                    "    vec3(-1, 0, 0)," +
                    "    vec3(1, 0, 0));" ,
                    "vec3 _vert_position;",
                    
                    "vec2 _vert_tex_diffuse_coord;",
                    
                    "ivec2 _vert_tex_light_coord;",
                    
                    "vec4 _vert_color_shade;",
                    
                    "vec3 _vert_normal;",

                    "layout(location = 0) in vec4 in_position;",
                    "layout(location = 1) in vec4 in_color;",
                    "layout(location = 2) in vec2 in_tex_diffuse_coord;",
                    "layout(location = 3) in ivec2 in_tex_light_coord;",
                    "void _vert_init() {" +
                    "    _vert_position = (in_position.xyz * " + parameters.vertScale + ") + 8.0f;" +
                    "    _vert_tex_diffuse_coord = in_tex_diffuse_coord;" +
                    "    _vert_tex_light_coord = in_tex_light_coord;" +
                    "    _vert_color_shade = vec4(in_color.rgb * in_color.a, 1.0);" +
                            "_vert_normal = normalLookup[floor(in_position.w + 0.5)];" +
                            "}",

                    // translated from sodium's chunk_parameters.glsl
                    // Comment on the struct:
                    // Older AMD drivers can't handle vec3 in std140 layouts correctly The alignment
                    // requirement is 16 bytes (4 float components) anyways, so we're not wasting
                    // extra memory with this, only fixing broken drivers.
                    "const int MAX_BATCH_SIZE =" + parameters.maxBatchSize + ";",
                    "struct ModelTransform {" +
                    "    vec3 translation;" +
                    "};",
                    
                    "layout(std140, binding = 1) uniform ModelTransforms {" +
                    "    ModelTransform transforms[MAX_BATCH_SIZE];" +
                    "};");

            tree.prependMain(t, "_vert_init();");
        }
    }

    public static Map<ShaderType, String> transformString(String vertex, String fragment, TerrainVertexType type, int maxBatchSize, boolean baseInstanced) {
        return transformer.transform(Map.of(ShaderType.VERTEX, vertex, ShaderType.FRAGMENT, fragment), new CoreShaderParameters(0.1f, type.getVertexRange(), maxBatchSize, baseInstanced));
    }
}