# חבילה מקומית מלאה עם אינדקס int8 מאוחד

`scripts/MergeSemanticShards.java` קורא שארדים קיימים, מאמת את טביעות המסד,
המודל והטוקנייזר, ומכמת את הווקטורים בשיטת `int8-maxabs-v1` של התוכנה.
מזהי השורות, מזהי הספרים וסימון ספרי הבסיס נשמרים. הפלט כולל
`index/shard-00`, מקטע Lucene אחד וגרף HNSW אחד, עם מניפסט חדש.
קובצי המקור נקראים בלבד; תיקיית הפלט חייבת להיות חדשה.

נדרשים JDK 25 ו־Lucene 10.4.0, הזמינים גם במטמון Gradle לאחר בניית הפרויקט.
בדוגמה הבאה `$library` הוא הנתיב לתיקיית `databases` של ההתקנה המקורית:

```powershell
$lucene = Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/org.apache.lucene/lucene-core/10.4.0" `
  -Recurse -Filter lucene-core-10.4.0.jar | Select-Object -First 1 -ExpandProperty FullName
java --add-modules jdk.incubator.vector -Xmx3g --class-path $lucene scripts/MergeSemanticShards.java `
  "$library/seforim.db.semantic/index" build/local-distribution/semantic/index `
  "$library/seforim.db" "$library/seforim.db.semantic/model"
Copy-Item -LiteralPath "$library/seforim.db.semantic/model" -Destination build/local-distribution/semantic/model -Recurse
```

הבנייה משתמשת בארבעה תהליכוני אינדוקס ובארבעה תהליכוני מיזוג. אין צורך להריץ
את מודל ההטמעה מחדש. קובץ `semantic.properties` נכתב רק אחרי שהושלמו
הכימות, המיזוג, בדיקת מספר המסמכים ובדיקת חיפוש KNN.

אריזה וחילוץ לצורך אימות:

```powershell
python -m pip install zstandard
python scripts/package-local-distribution.py --database-dir $library `
  --semantic-dir build/local-distribution/semantic --output build/local-distribution/release
python scripts/package-local-distribution.py --verify --output build/local-distribution/release `
  --extract-semantic build/local-distribution/verified-semantic
```

החבילה כוללת את המסד, שני אינדקסי הטקסט, הקטלוג, המסד המילולי, מידע ההפצה,
ספריית ה־PDF, המודל, הטוקנייזר והאינדקס הסמנטי. כל התוכן נדחס ברמת 22.
הכתיבה מתבצעת ישירות לחלקים של 1,900,000,000 בתים, ללא עותק נוסף של הארכיון
המלא. אם מספיק חלק אחד, שמו יהיה `seforim_bundle.tar.zst`.

אפשר להכין את הבסיס במקביל לבניית האינדקס:

```powershell
python scripts/package-local-distribution.py --database-dir $library --base-only `
  --output build/local-distribution/base-stage
python scripts/package-local-distribution.py --database-dir $library `
  --semantic-dir build/local-distribution/semantic --base-archive build/local-distribution/base-stage `
  --output build/local-distribution/release
```

במסלול זה החבילה המלאה מכילה שני ארכיוני tar ופריימים של zstd ברצף.
שניהם נדחסים ברמת 22, והבסיס מועתק ללא דחיסה מחדש. המתקין תומך במבנה זה
ובחלקים המפוצלים. בייבוא מקומי בוחרים את הקובץ `.part01` (או `.tar.zst` אם יש רק קובץ אחד), כאשר כל החלקים
נמצאים באותה תיקייה.

`distributions.json` מתעד את גודל כל חלק, SHA-256 של החלקים ושל כל הקבצים
הארוזים, רמת הדחיסה, קידוד הווקטורים וטביעת המסד. `checksums.sha256` מיועד
לבדיקת ההורדה. פעולת `--verify` קוראת את כל החלקים בסדר המניפסט, מאמתת
כל קובץ ואת סיומות פריימי zstd, ומפיקה `verification.json` רק לאחר הצלחה.
אפשר לחלץ במהלכה את הרכיבים הסמנטיים בלבד לבדיקת Lucene בלי לחלץ עותק נוסף
של המסד וה־PDF.

אחרי חילוץ באמצעות התוכנה אפשר לבדוק גם את כל הקבצים שהמתקין כתב בפועל:

```powershell
python scripts/package-local-distribution.py --output build/local-distribution/release `
  --verify-installed "$installed/databases"
```

הבדיקה משווה את הגודל ואת SHA-256 של כל קובץ מותקן למניפסט ההפצה,
כולל המסד, האינדקסים, המודל וכל קובצי ה־PDF, ומפיקה `installed-verification.json`.
`VerifyFullDistribution.java` מפעיל את המתקין של התוכנה בתיקייה חדשה,
בודק פתיחת אינדקסי הטקסט, זיהוי PDF וזיהוי האינדקס הסמנטי, ומריץ
שלוש שאילתות בעברית עם המודל האמיתי, התאמה למסד ומסנני ספרים.

בדיקות ממוקדות:

```powershell
javac -cp $lucene -d build/local-distribution/tools scripts/MergeSemanticShards.java scripts/TestMergeSemanticShards.java
java --add-modules jdk.incubator.vector -cp "build/local-distribution/tools;$lucene" `
  TestMergeSemanticShards build/local-distribution/merge-fixture
python -m unittest discover -s scripts -p test_local_distribution.py
./gradlew.bat :SeforimApp:jvmTest --tests '*ExtractUseCaseTest' --tests '*DistributionArchivesTest' `
  --tests '*TabsViewModelIntegrationTest' --tests '*SemanticBundleArchivesTest'
```

כאשר חיפוש HNSW באינדקס int8 ממצה רכיב בגרף ומחזיר פחות ממספר המועמדים
המבוקש, `CompleteKnnByteVectorQuery` משלים בחיפוש מדויק על הווקטורים המאושרים
לפי המסנן. ההשלמה שומרת על התוצאות ומסנני הספרים, אך עשויה לייקר שאילתות.
`BenchmarkSemanticDistribution.java` משווה את החיפוש המקורי למאוחד עם אותם
וקטורי שאילתה ו־150 מועמדים, שני סבבי חימום ו־50 מדידות לכל אינדקס.
המדידה אינה כוללת יצירת embeddings או הצגת התוצאות בממשק.

בדיקת מהירות מקצה לקצה: `SearchLatencyIntegrationTest` מפעיל את המודל, האינדקסים,
המסד, ספק קטעי הטקסט, `SearchResultViewModel` וזרם התוצאות המוצגות.
מריצים עם `-Dzayit.search.benchmark.database=<seforim.db>` ו־
`-Dzayit.search.benchmark.ui=true -Dzayit.search.benchmark.maxMillis=5000`.
הבדיקה נכשלת כששאילתה חורגת מחמש שניות, כולל חיפוש ראשון בתהליך חדש.
היא מודדת הגעת תוצאות למצב הממשק, ולא ציור פריים או ביצועים בכל חומרה ושאילתה.

חיפוש int8 עם מסנן ספרים סורק בדיוק את הווקטורים המאושרים כדי למנוע
מעבר על מיליוני צמתים שנפסלו בגרף. ללא מסנן נשמר חיפוש הגרף עם תקציב
ביקורים והשלמה מדויקת כשחסרים מועמדים. החיפוש הטקסטואלי והסמנטי רצים
במקביל. ספק הקטעים מאחד חלונות הקשר חופפים בלבד ואינו קורא ספר שלם
בין תוצאות רחוקות.

המתקין מחשב SHA-256 של המסד בזמן החילוץ ושומר מטמון מקומי.
`SemanticFileFingerprint` מבטל מטמון כשנתיב קנוני, גודל, זמני שינוי ויצירה
או מזהה קובץ משתנים. זהו מטמון לקורפוס קבוע, לא אימות נגד שינוי זדוני
של המטמון ושימור מכוון של מטא־נתוני הקובץ. בהתקנה ישנה נדרש אימות מלא
ראשוני; בהתקנה חדשה הוא מחושב כבר בזמן החילוץ.

טעינת הטוקנייזר ו־ONNX מתבצעת במקביל. התוכנה מכינה את המנוע ברקע
במסך הראשי ומשתמשת באותו מודל בחיפוש הראשון. בדיקת הזמינות אינה
טוענת עותק נוסף של המודל.

בדיקת הרשת של DJL לזיהוי סביבת EC2 מושבתת לפני טעינת הטוקנייזר המקומי.
בהרצת האימות הסופית בתהליך JVM חדש עברו 22 חיפושים בשכבת
`SearchResultViewModel.visibleResultsFlow` את סף 5 השניות: הראשון 4,821 מילישניות.
זו בדיקת הנתונים המזינים את רשימת התוצאות, לא מדידת פריים מצויר.
בהרצה מקדימה נמדדו 9,645 מילישניות בחיפוש הראשון; לכן אין להסיק שהסף
מובטח בכל הפעלה קרה או עומס מחשב. כל המדידות וההסתייגויות נשמרות
ב־`release/ui-search-verification.json` וב־`release/completion.json`.
התיקונים דורשים בנייה והתקנה של גרסת התוכנה המעודכנת; החבילה מכילה
את נתוני הספרייה ולא מתקין מעודכן של התוכנה.
