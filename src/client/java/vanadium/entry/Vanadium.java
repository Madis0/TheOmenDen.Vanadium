package vanadium.entry;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vanadium.configuration.VanadiumConfig;
import vanadium.resources.*;

public class Vanadium implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Vanadium.class);
    public static final String MODID = "vanadium";
    public static final String COLORMATIC_ID = "colormatic";
    public static final ResourceLocation OVERWORLD_ID = BuiltinDimensionTypes.OVERWORLD.registry();

    public static final BiomeColorMappingResource WATER_COLORS = new BiomeColorMappingResource(new ResourceLocation(MODID, "colormap/water"));
    public static final BiomeColorMappingResource UNDERWATER_COLORS = new BiomeColorMappingResource(new ResourceLocation(MODID, "colormap/underwater"));
    public static final BiomeColorMappingResource UNDERLAVA_COLORS = new BiomeColorMappingResource(new ResourceLocation(MODID, "colormap/underlava"));
    public static final BiomeColorMappingResource SKY_COLORS = new BiomeColorMappingResource(new ResourceLocation(MODID, "colormap/sky0"));
    public static final BiomeColorMappingResource FOG_COLORS = new BiomeColorMappingResource(new ResourceLocation(MODID, "colormap/fog0"));
    public static final BiomeColorMappingResource BIRCH_COLORS = new BiomeColorMappingResource(new ResourceLocation(MODID, "colormap/birch"));
    public static final BiomeColorMappingResource SPRUCE_COLORS = new BiomeColorMappingResource(new ResourceLocation(MODID, "colormap/pine"));
    public static final LinearColorMappingResource PUMPKIN_STEM_COLORS = new LinearColorMappingResource(new ResourceLocation(MODID, "colormap/pumpkinstem.png"));
    public static final LinearColorMappingResource MELON_STEM_COLORS = new LinearColorMappingResource(new ResourceLocation(MODID, "colormap/melonstem.png"));
    public static final LinearColorMappingResource REDSTONE_COLORS = new LinearColorMappingResource(new ResourceLocation(MODID, "colormap/redstone.png"));
    public static final LinearColorMappingResource MYCELIUM_PARTICLE_COLORS = new LinearColorMappingResource(new ResourceLocation(MODID, "colormap/myceliumparticle.png"));
    public static final LinearColorMappingResource LAVA_DROP_COLORS = new LinearColorMappingResource(new ResourceLocation(MODID, "colormap/lavadrop.png"));
    public static final LinearColorMappingResource DURABILITY_COLORS = new LinearColorMappingResource(new ResourceLocation(MODID, "colormap/durability.png"));
    public static final LinearColorMappingResource EXPERIENCE_ORB_COLORS = new LinearColorMappingResource(new ResourceLocation(MODID, "colormap/xporb.png"));
    public static final CustomBiomeColorMappingResource CUSTOM_BLOCK_COLORS = new CustomBiomeColorMappingResource();
    public static final GlobalLightMappingResource LIGHTMAP_PROPERTIES = new GlobalLightMappingResource(new ResourceLocation(MODID, "lightmap.json"));
    public static final LightMappingResource LIGHTMAPS = new LightMappingResource(new ResourceLocation(MODID, "lightmap"));
    public static final GlobalColorResource COLOR_PROPERTIES = new GlobalColorResource(new ResourceLocation(MODID, "color"));
    private static VanadiumConfig config = new VanadiumConfig();

    public static VanadiumConfig getCurrentConfiguration() {
        return config;
    }

    public static ResourceLocation getDimensionId(Level world) {
        ResourceLocation dimensionId = world.dimension().location();

        if(dimensionId == null){
            return OVERWORLD_ID;
        }

        return dimensionId;
    }

    public static ResourceLocation getBiomeId(RegistryAccess manager, Biome biome) {

        ResourceKey biomeId = manager.registry(Registries.BIOME).get().key();

        if(biomeId == null){
            return Biomes.PLAINS.location();
        }

        return biomeId.location();
    }

    public static ResourceKey<Biome> getBiomeKey(RegistryAccess manager, Biome biome) {
        return manager.lookup(Registries.BIOME)
                      .get()
                      .getOrThrow(Biomes.PLAINS)
                      .key();
    }

    public static <T> T getRegistryValue(Registry<T> registry, Holder<T> entry) {
        var optionalRegistryKey = entry.unwrapKey();
        if(optionalRegistryKey.isPresent()) {
            return registry.get(optionalRegistryKey.get());
        }
        return entry.value();
    }

    @Override
    public void onInitializeClient() {
        AutoConfig.register(VanadiumConfig.class, GsonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(VanadiumConfig.class).getConfig();
        LOGGER.info("{} is colorizing your packs \uD83C\uDFA8 ", MODID.toUpperCase());

        ResourceManagerHelper client = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
        client.registerReloadListener(WATER_COLORS);
        client.registerReloadListener(UNDERWATER_COLORS);
        client.registerReloadListener(UNDERLAVA_COLORS);
        client.registerReloadListener(FOG_COLORS);
        client.registerReloadListener(BIRCH_COLORS);
        client.registerReloadListener(SPRUCE_COLORS);
        client.registerReloadListener(REDSTONE_COLORS);
        client.registerReloadListener(PUMPKIN_STEM_COLORS);
        client.registerReloadListener(MELON_STEM_COLORS);
        client.registerReloadListener(MYCELIUM_PARTICLE_COLORS);
        client.registerReloadListener(LAVA_DROP_COLORS);
        client.registerReloadListener(DURABILITY_COLORS);
        client.registerReloadListener(EXPERIENCE_ORB_COLORS);
        client.registerReloadListener(LIGHTMAP_PROPERTIES);
        client.registerReloadListener(LIGHTMAPS);
    }
}