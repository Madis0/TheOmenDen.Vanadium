package vanadium.properties;

import com.google.gson.JsonSyntaxException;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import vanadium.Vanadium;
import vanadium.enums.ColumnLayout;
import vanadium.enums.Format;
import vanadium.models.GridEntry;
import vanadium.models.VanadiumColor;
import vanadium.util.BiomeNameTracer;
import vanadium.util.ColumnBounds;
import vanadium.util.GsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

public class ColorMappingProperties {

    private static final Logger LOGGER = LogManager.getLogger();
    private transient final boolean isUsingOptifine;

    private final ResourceLocation identifier;
    private final Format format;
    private final Collection<ApplicableBlockStates> blockStates;
    private final ResourceLocation source;
    private final VanadiumColor color;
    private ColumnLayout layout;
    private final int yVariance;
    private final int yOffset;
    private final Map<ResourceLocation, ColumnBounds> columnsByBiome;

    private static final ColumnBounds DEFAULT_COLUMN_BOUNDARIES = new ColumnBounds(0, 1);

    private ColorMappingProperties(ResourceLocation identifier, Settings settings){
        this.identifier = identifier;
        this.isUsingOptifine = this.identifier.getPath().endsWith(".properties");
        this.blockStates = new ArrayList<>();
        this.format = settings.format;
        ResourceLocation source =ResourceLocation.tryParse(settings.source);
        if(source == null) {
            LOGGER.error("Unable to parse {}: Invalid source location '{}', using filename as fallback option", identifier, settings.source );
            source = new ResourceLocation(makeSourceFromFileName(identifier));
        }

        this.source = source;

        this.color = settings.color;

        this.layout = Objects.requireNonNullElse(settings.layout, this.isUsingOptifine
                ? ColumnLayout.OPTIFINE
                : ColumnLayout.DEFAULT);

        this.yVariance = 0;

        this.yOffset = 0;

        if(settings.grid != null) {
            this.columnsByBiome = new HashMap<>();
            int nextColumn = 0;
            List<GridEntry> grid = settings.grid;
            for (GridEntry entry : grid) {
                if (entry.column >= 0) {
                    nextColumn = entry.column;
                }

                ColumnBounds bounds = new ColumnBounds(nextColumn, entry.width);
                nextColumn += entry.width;

                entry.biomes
                        .stream()
                        .map(biomeId -> BiomeNameTracer.updateBiomeName(biomeId, this.identifier))
                        .filter(Objects::nonNull)
                        .forEach(updatedBiomeNameId -> columnsByBiome.put(updatedBiomeNameId, bounds));
            }
        } else if(settings.biomes != null) {
            this.columnsByBiome = new HashMap<>();

            settings.biomes
                    .entrySet()
                    .forEach(this::checkForUpdatedBiomeResourceLocation);
        } else {
            this.columnsByBiome = null;
        }
    }

    public ResourceLocation getResourceLocation() {
        return this.identifier;
    }

    public Format getFormat() {
        return this.format;
    }

    public ResourceLocation getSource() {
        return this.source;
    }

    public int getYVariance() {
        return this.yVariance;
    }

    public int getYOffset() {
        return this.yOffset;
    }

    public ColumnBounds getColumn(ResourceKey<Biome> biomeKey, ResourceKey<Biome> biomeResourceKey){
        if(this.format != Format.GRID) {
            throw new IllegalStateException("Column layout is not set to GRID");
        }

        if(biomeKey == null) {
            return DEFAULT_COLUMN_BOUNDARIES;
        }

        ResourceLocation biomeId = Biomes.PLAINS.location();

        if(this.columnsByBiome != null) {
            ColumnBounds column = this.columnsByBiome.get(biomeId);
            if(column!= null) {
                return column;
            }
            throw new IllegalArgumentException("Biome " + biomeId + " not found in color mapping");
        }

        return switch(this.layout) {
            case DEFAULT -> DefaultColumns.getDefaultBoundaries(biomeKey);
            case OPTIFINE -> DefaultColumns.getOptifineBoundaries(biomeKey, biomeResourceKey);
            case LEGACY -> DefaultColumns.getLegacyBoundaries(biomeKey, biomeResourceKey, this.isUsingOptifine);
            case STABLE -> DefaultColumns.getStableBoundaries(biomeKey);
        };
    }

    public Set<ResourceLocation> getApplicableBiomes() {
        return new HashSet<>(this.columnsByBiome.keySet());
    }

    public Set<Block> getApplicableBlocks() {
        return blockStates
                .stream()
                .filter(a -> a.specialKey == null
                && a.states.isEmpty())
                .map(a -> a.defaultBlock)
                .collect(Collectors.toSet());
    }

    public Set<BlockState> getApplicableBlockStates() {
        return blockStates
                .stream()
                .filter(b -> b.specialKey == null)
                .flatMap(a -> a.states.stream())
                .collect(Collectors.toSet());
    }

    public Map<ResourceLocation, Collection<ResourceLocation>> getApplicableSpecialIds() {
        return blockStates
               .stream()
               .filter(a -> a.specialKey!= null)
               .collect(Collectors.toMap(a -> a.specialKey, a -> a.specialIds));
    }

    public VanadiumColor getColor() {
        return color;
    }

    @Override
    public String toString() {
        return String.format("ColorMappingProperties { format=%s, blocks=%s, source=%s, color=%x, yVariance=%x, yOffset=%x }",
                format,
                blockStates,
                source,
                color == null ? 0 : color.rgb(),
                yVariance,
                yOffset);
    }

    public static ColorMappingProperties loadProperties(ResourceManager manager, ResourceLocation identifier, boolean isCustom) {
        try(InputStream stream = manager.getResourceOrThrow(identifier).open();
            Reader reader = GsonUtils.getJsonReader(stream, identifier, k -> k, "blocks"::equals)) {
           return loadFromJson(reader, identifier, isCustom);
        } catch(IOException e) {
            return loadFromJson(new StringReader("{}"), identifier, isCustom);
        }
    }

    private static ColorMappingProperties loadFromJson(Reader jsonReader, ResourceLocation identifier, boolean isCustom) {
     Settings settings;

     try{
         settings = GsonUtils.PROPERTY_GSON.fromJson(jsonReader, Settings.class);

         if(settings == null) {
             settings = new Settings();
         }

     } catch(Exception e) {
         LOGGER.error("Failed to load color mapping properties {}: {}", identifier, e.getMessage());
         settings = new Settings();
     }

     if(settings.format == null) {
         settings.format = Vanadium.COLOR_PROPERTIES.getProperties().getDefaultFormat();
     }

     if(settings.layout == null) {
         settings.layout = Vanadium.COLOR_PROPERTIES.getProperties().getDefaultColumnLayout();
     }

     if(isCustom
             && (settings.blockStates == null
             || settings.blockStates.isEmpty())) {
         String blockId = identifier.getPath();
         blockId = blockId.substring(blockId.lastIndexOf('/') + 1,
                 blockId.lastIndexOf('.'));
         settings.blockStates = new ArrayList<>();

         try {
             settings.blockStates.add(GsonUtils.PROPERTY_GSON.fromJson(blockId, ApplicableBlockStates.class));
         } catch (JsonSyntaxException e) {
             LOGGER.error("Failed to load color mapping properties {}: {}", identifier, e.getMessage());
         }
     }

     if(!isCustom && shouldDisableOptionsForNonCustomColormappings(settings)) {
        settings.biomes = null;
        settings.grid = null;
        settings.blockStates = Collections.emptyList();
     }

     if(settings.source == null) {
         settings.source = makeSourceFromFileName(identifier);
     }

     settings.source = GsonUtils.resolveRelativeResourceLocation(settings.source, identifier);

     return new ColorMappingProperties(identifier, settings);
    }

    private static String makeSourceFromFileName(ResourceLocation identifier) {
        String filePath = identifier.getPath();
        filePath = filePath.substring(0, filePath.lastIndexOf('.')) + ".png";
        return filePath;
    }

    private static boolean shouldDisableOptionsForNonCustomColormappings(Settings settings) {
        return settings.biomes != null
                || settings.grid != null
                || settings.blockStates != null;
    }

    private void checkForUpdatedBiomeResourceLocation(Map.Entry<ResourceLocation, Integer> entry) {
        ResourceLocation updatedBiomeId = BiomeNameTracer.updateBiomeName(entry.getKey(), this.identifier);
        if (updatedBiomeId != null) {
            this.columnsByBiome.put(updatedBiomeId, new ColumnBounds(entry.getValue(), 1));
        }
    }

    private static class Settings {
        Format format = null;
        Collection<ApplicableBlockStates> blockStates = null;
        VanadiumColor color = null;
        String source = null;
        ColumnLayout layout = null;
        int yVariance = 0;
        int yOffset = 0;
        Map<ResourceLocation, Integer> biomes = null;
        List<GridEntry> grid =null;
    }
}

