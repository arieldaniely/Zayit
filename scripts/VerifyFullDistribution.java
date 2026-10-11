import java.nio.file.*;
import java.util.Properties;
import com.russhwolf.settings.PropertiesSettings;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings;
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseLocationKt;
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseUtilsKt;
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractUseCase;
import io.github.kdroidfilter.seforimapp.features.search.SemanticAssetsManager;
import io.github.kdroidfilter.seforimapp.features.pdf.TalmudPdfService;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.FSDirectory;

/** Exercise the real desktop installer against the complete release in a disposable installation root. */
public class VerifyFullDistribution {
    public static void main(String[] args) throws Exception {
        var root = Path.of(args[1]);
        if (Files.exists(root.resolve("databases"))) throw new IllegalArgumentException("Use a fresh installation root");
        Files.createDirectories(root);
        AppSettings.INSTANCE.initialize(new PropertiesSettings(new Properties(), properties -> Unit.INSTANCE));
        String previous = AppSettings.INSTANCE.getDatabasePath();
        try {
            DatabaseLocationKt.selectInstallationRoot(root.toFile());
            if (!DatabaseLocationKt.databaseInstallDirectory().getCanonicalFile()
                    .equals(root.resolve("databases").toFile().getCanonicalFile())) {
                throw new IllegalStateException("An environment override points outside the verification root");
            }
            Path seededData = root.resolve("databases");
            for (int shard = 0; shard < 8; shard++) {
                Path old = seededData.resolve(String.format("seforim.db.semantic/index/shard-%02d/segments_999", shard));
                Files.createDirectories(old.getParent());
                Files.writeString(old, "legacy float32 index marker");
            }
            for (String component : new String[] {"seforim.db.lucene", "seforim.db.lookup.lucene"}) {
                Files.createDirectories(seededData.resolve(component));
                Files.writeString(seededData.resolve(component + "/segments_999"), "legacy commit marker");
            }
            var progress = new int[] {-1};
            String installed = BuildersKt.<String>runBlocking(EmptyCoroutineContext.INSTANCE, (scope, continuation) ->
                new ExtractUseCase().extractToDatabase(args[0], fraction -> {
                    int percent = Math.round(fraction * 100);
                    if (percent / 10 > progress[0]) {
                        progress[0] = percent / 10;
                        System.out.println("Desktop installer: " + percent + "%");
                    }
                    return Unit.INSTANCE;
                }, continuation));
            if (!installed.equals(root.resolve("databases/seforim.db").toAbsolutePath().toString())) {
                throw new AssertionError("Incorrect database location: " + installed);
            }
            Path data = Path.of(installed).getParent();
            for (String name : new String[] {"catalog.pb", "lexical.db", "release_info.txt",
                    "seforim.db.lucene", "seforim.db.lookup.lucene", "תלמוד בבלי"}) {
                if (!Files.exists(data.resolve(name))) throw new AssertionError("Missing " + name);
            }
            try (var shards = Files.list(data.resolve("seforim.db.semantic/index"))) {
                if (shards.filter(Files::isDirectory).count() != 1) throw new AssertionError("Old shards survived upgrade");
            }
            for (String component : new String[] {"seforim.db.lucene", "seforim.db.lookup.lucene",
                    "seforim.db.semantic/index/shard-00"}) {
                if (Files.exists(data.resolve(component + "/segments_999"))) throw new AssertionError("Old commit survived upgrade");
            }
            if (!TalmudPdfService.INSTANCE.isInstalled()) throw new AssertionError("PDF library not recognized");
            for (String name : new String[] {"seforim.db.lucene", "seforim.db.lookup.lucene"}) {
                try (var directory = FSDirectory.open(data.resolve(name)); var reader = DirectoryReader.open(directory)) {
                    if (reader.numDocs() == 0) throw new AssertionError("Empty text index: " + name);
                    System.out.println("PASS: text index recognized: " + name + " (" + reader.numDocs() + " documents)");
                }
            }
            if (!SemanticAssetsManager.INSTANCE.validatedReady()) throw new AssertionError("Semantic bundle not recognized");
            VerifySemanticDistribution.main(new String[] {data.resolve("seforim.db.semantic").toString(), installed});
            Files.writeString(Path.of(args[2]),
                "{\"verified\":true,\"desktopExtraction\":true,\"upgradeFromEightShards\":true,\"pdfRecognized\":true,\"textIndexesRecognized\":true,\"semanticRecognized\":true," +
                "\"modelLoaded\":true,\"queries\":3,\"shardCount\":1,\"vectorEncoding\":\"int8-maxabs-v1\"}");
            System.out.println("PASS: full desktop extraction, PDF recognition, semantic recognition and actual search");
        } finally {
            AppSettings.INSTANCE.setDatabasePath(previous);
            DatabaseUtilsKt.resetDatabasePathCache();
        }
    }
}
