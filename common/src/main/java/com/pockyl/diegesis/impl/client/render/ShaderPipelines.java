package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Tells a shader pack's renderer (Iris) which of its programs draws each of the library's own pipelines. It knows the game's
 * pipelines only: one it does not know it draws with a program of another vertex layout, and a scene comes out in streaks of
 * the atlas. Without Iris this does nothing.
 */
final class ShaderPipelines {
    private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String PROGRAM = "net.irisshaders.iris.api.v0.IrisProgram";
    private static final MethodHandle ASSIGN = find();

    private ShaderPipelines() {
    }

    /** Has {@code pipeline} drawn by the shader pack's program {@code program} (a name of Iris' {@code IrisProgram}). */
    static void assign(RenderPipeline pipeline, String program) {
        if (ASSIGN == null) {
            return;
        }
        try {
            Class<?> programs = Class.forName(PROGRAM, false, ShaderPipelines.class.getClassLoader());
            ASSIGN.invoke(pipeline, enumConstant(programs, program));
        } catch (Throwable e) {
            // An Iris without that program: the pipeline is drawn as it was.
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static MethodHandle find() {
        try {
            ClassLoader loader = ShaderPipelines.class.getClassLoader();
            Class<?> api = Class.forName(API, false, loader);
            Class<?> program = Class.forName(PROGRAM, false, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle instance = lookup.findStatic(api, "getInstance", MethodType.methodType(api));
            MethodHandle assign = lookup.findVirtual(api, "assignPipeline", MethodType.methodType(void.class, RenderPipeline.class, program));
            return MethodHandles.foldArguments(assign, instance);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
