slangc -matrix-layout-row-major -target spirv -o terrain.spv terrain.slang -fvk-use-entrypoint-name -g
slangc -matrix-layout-row-major -target spirv -o task_cull.spv terrain.slang -entry taskMain -stage amplification -fvk-use-entrypoint-name -g
slangc -matrix-layout-row-major -target spirv -o terrain_mesh.spv terrain.slang -entry meshMain -stage mesh -fvk-use-entrypoint-name -g
slangc -matrix-layout-row-major -target spirv -o instance_cull.spv instance_cull.slang -fvk-use-entrypoint-name -g
