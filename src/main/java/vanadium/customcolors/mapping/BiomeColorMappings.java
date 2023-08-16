package vanadium.customcolors.mapping;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.biome.Biome;
import vanadium.biomeblending.storage.ColorMappingStorage;
import vanadium.customcolors.interfaces.VanadiumResolver;
import vanadium.defaults.DefaultVanadiumResolverProviders;
import vanadium.models.ColorMappingProperties;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public final class BiomeColorMappings {
    private static final ColorMappingStorage<Block> colorMappingsByBlock = new ColorMappingStorage<>(DefaultVanadiumResolverProviders.BLOCK_PROVIDER);
    private static final ColorMappingStorage<BlockState> colorMappingsByState = new ColorMappingStorage<>(DefaultVanadiumResolverProviders.BLOCK_STATE_PROVIDER);
    private static final ColorMappingStorage<Fluid> colorMappingsByFluidFog = new ColorMappingStorage<>(DefaultVanadiumResolverProviders.FLUID_FOG_PROVIDER);
    private static final ColorMappingStorage<Identifier> skyFogColorMappings = new ColorMappingStorage<>(DefaultVanadiumResolverProviders.SKY_FOG_PROVIDER);
    private static final ColorMappingStorage<Identifier> skyColorMappings = new ColorMappingStorage<>(DefaultVanadiumResolverProviders.SKY_PROVIDER);

    private BiomeColorMappings() {
    }

    public static VanadiumResolver getTotalSky(Identifier dimensionId) {
        return skyColorMappings.getVanadiumResolver(dimensionId);
    }

    public static VanadiumResolver getTotalSkyFog(Identifier dimensionId) {
        return skyFogColorMappings.getVanadiumResolver(dimensionId);
    }

    public static BiomeColorMapping getFluidFog(DynamicRegistryManager manager, Fluid fluid, Biome biome) {
        return colorMappingsByFluidFog.getColorMapping(manager, fluid, biome);
    }

    public static void addBiomeColorMapping(BiomeColorMapping biomeColorMap) {
        ColorMappingProperties properties = biomeColorMap.getProperties();
        Set<Identifier> biomes = properties.getApplicableBiomes();
        colorMappingsByState.addColorMapping(biomeColorMap, properties.getApplicableBlockStates(), biomes);
        colorMappingsByBlock.addColorMapping(biomeColorMap, properties.getApplicableBlocks(), biomes);

        properties
                .getApplicableSpecialIds()
                .forEach((key, value) -> {
                    switch (key.toString()) {
                        case "vanadium:sky", "colormatic:sky" ->
                                skyColorMappings.addColorMapping(biomeColorMap, value, biomes);
                        case "vanadium:sky_fog", "colormatic:sky_fog" ->
                                skyFogColorMappings.addColorMapping(biomeColorMap, value, biomes);
                        case "vanadium:fluid_fog", "colormatic:fluid_fog" -> {
                            Collection<Fluid> fluids = value
                                    .stream()
                                    .map(Registries.FLUID::get)
                                    .collect(Collectors.toList());
                            colorMappingsByFluidFog.addColorMapping(biomeColorMap, fluids, biomes);
                        }
                    }
                });
    }

    public static void resetColorMappings() {
        colorMappingsByState.clearMappings();
        colorMappingsByBlock.clearMappings();
        colorMappingsByFluidFog.clearMappings();
        skyColorMappings.clearMappings();
        skyFogColorMappings.clearMappings();
    }

    public static boolean isCustomColored(BlockState state) {
        return colorMappingsByBlock.contains(state.getBlock())
                || colorMappingsByState.contains(state);
    }

    public static boolean isItemCustomColored(BlockState state) {
        return colorMappingsByBlock.getFallbackColorMapping(state.getBlock()) != null
                || colorMappingsByState.getFallbackColorMapping(state) != null;
    }

    public static boolean isFluidFogCustomColored(Fluid fluid) {
        return colorMappingsByFluidFog.contains(fluid);
    }

    public static int getBiomeColorMapping(BlockState state, BlockRenderView world, BlockPos pos) {
        if(world != null && pos != null) {
            var resolver = colorMappingsByState.getExtendedResolver(state);

            if(resolver == null) {
                throw new IllegalArgumentException(String.valueOf(state));
            }

            return resolver.resolveExtendedColor(world, pos);
        }

        BiomeColorMapping biomeColorMap = colorMappingsByState.getFallbackColorMapping(state);

        if(biomeColorMap == null) {
            biomeColorMap = colorMappingsByBlock.getFallbackColorMapping(state.getBlock());
        }

        if(biomeColorMap != null) {
            return biomeColorMap.getDefaultColor();
        }

        return 0xffffff;
    }
}
