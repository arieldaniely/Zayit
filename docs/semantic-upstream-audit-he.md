# ממצאי המנוע הסמנטי הציבוריים והפרדת המימוש המקומי

נבדק ב־28.10.2026. המקורות מקובעים לקומיטים כדי להבחין בין עובדה להשערה.

## מה ניתן להוכיח מהקוד הציבורי

- [Zayit, קומיט 44febc5](https://github.com/kdroidFilter/Zayit/tree/44febc5bcfc7fdf56fbc773bffe9dd511fd46243)
  משאיר את מתאם החיפוש המשולב פתוח תחת `SeforimApp/addons/semantic/enabled`.
  `SemanticSearchFeature` מספק נקודת חיבור; בבניית קהילה נבחר מימוש `disabled`
  שבו `installedSemanticSearch` הוא null והחיפוש נשאר מילולי.
- [המתאם הציבורי](https://github.com/kdroidFilter/Zayit/blob/44febc5bcfc7fdf56fbc773bffe9dd511fd46243/SeforimApp/addons/semantic/enabled/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/HybridSearchEngine.kt)
  קורא ל־`io.github.kdroidfilter.seforim.semantic.SemanticSearch`:
  `isAvailable(indexDir)`, `open(indexDir)`, `search(query, count, baseOnly, bookIds)`,
  `bestPassages(query, texts)` ו־`close()`. אין במתאם גישה למשקלי המודל.
- ה־API משתמש באינדקס Lucene משותף לחיפוש המילולי ולווקטורים; החיבור למסד נעשה
  בחוץ לפי `lineId`. המודל מתואר כתוכן שנמצא בחבילת SeforimEmbedding.
- החיבור נשאר עצל ומוגן ב־Mutex; הוא מכבה את מעקב DJL באמצעות
  `OPT_OUT_TRACKING`, ורץ ברקע. חיפוש מילולי וסמנטי מתבצעים במקביל.
- דירוג משולב הוא RRF עם `K=60`: סכום `1/(60+rank)` של כל מסלול.
  נלקחים **150 מועמדים מכל מסלול**, ואז מציגים עמודים מתוך הרשימה שבזיכרון.
  לכן עימוד אינו מבטל את תקרת האחזור. ציון RRF אינו סבירות לרלוונטיות.
- סינון ספרים וספרי יסוד נתמך בשני המסלולים. קטגוריות, מזהי שורות וביטויים
  מדויקים במירכאות חוזרים למסלול המילולי. השלמת כותרות, facets והדגשות מילוליות
  מואצלות למנוע המילולי; קטעים סמנטיים מחושבים לעמוד המוצג.
- [הגדרות הספרייה](https://github.com/kdroidFilter/SeforimLibrary/blob/fb2af88e1f043a43998a094f87244aff8abb0b7a/settings.gradle.kts)
  מגדירות חבילת Maven פרטית ב־GitHub Packages במאגר `kdroidFilter/SeforimEmbedding`.
  טוקן נמצא דרך property, משתנה סביבה, טוקן Siddur או בדיקת הרשאות ב־gh.
  בהיעדרו `semanticEnabled=false`; המנוע המילולי נשאר בר־בנייה.
- [קטלוג התלויות](https://github.com/kdroidFilter/SeforimLibrary/blob/fb2af88e1f043a43998a094f87244aff8abb0b7a/gradle/libs.versions.toml)
  חושף גרסה `0.4.2`, שתי חבילות `seforim-semantic` ו־`seforim-semantic-fp32`,
  ONNX Runtime `1.30.0` ו־Lucene `10.5.2`.
- [מחולל האינדקס](https://github.com/kdroidFilter/SeforimLibrary/blob/fb2af88e1f043a43998a094f87244aff8abb0b7a/generator/searchindex/build.gradle.kts)
  מקבל גם וקטורים מוכנים דרך `vectorsBin`, או בנייה ללא וקטורים דרך `noVectors`.
  קידוד אוטומטי תלוי בחבילה הפרטית; CUDA נבחר ב־Linux עם NVIDIA, ואחרת CPU.
  ההערות מעריכות כ־5.5GB לווקטורים ומגדירות heap גדול יותר כשיש קידוד.

## מה אי אפשר להסיק

עדות נוספת מהמחולל הציבורי: [LuceneTextIndexWriter](https://github.com/kdroidFilter/SeforimLibrary/blob/fb2af88e1f043a43998a094f87244aff8abb0b7a/generator/searchindex/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/searchindex/lucene/LuceneTextIndexWriter.kt)
משתמש ב־`KnnByteVectorField` עם COSINE, כימות לפי max-absolute-value,
ו־`Lucene99HnswVectorsFormat(16, 100, ...)`. הוא בונה שדה וקטורי נפרד לספרי יסוד
כדי להימנע מסינון גרף של הקורפוס כולו. אלה עובדות על **כותב האינדקס הפתוח**;
הן עדיין אינן חושפות את אלגוריתם האחזור בחבילה הפרטית.

הקוד הפתוח אינו חושף את מימוש החבילה הפרטית, נתוני האימון, פונקציית ההפסד,
ארכיטקטורת המקודד, כיול הציונים, או מדדי דיוק וזמן אמינים. שם fp32 מעיד על
וריאנט אריזה; הוא אינו מוכיח שיפור איכות. אין כאן שחזור של קוד פרטי או של משקלים.
מגבלות הידע האלה חלות גם על טענות לגבי חיתוך, HNSW או quantization במנוע הרשמי
החדש: יש לבדוק כל פרט מול מקור ציבורי נוסף לפני ייחוסו אליו.

## המימוש המקומי והגבול החדש

המימוש שכבר קיים בפורק שלנו שונה: ONNX Round 2 חיצוני, 256 ממדים, תפקידי
QUERY/PASSAGE, עד 256 טוקנים, נרמול עברית/ארמית, וקטורי int8 וקבלה של אינדקסי
float32 ישנים, manifests וחתימות SHA-256. האינדקס הסמנטי מותקן בנפרד מן המילולי.

`SeforimLibrary/SeforimSemantic` הוא מאגר Git נפרד עם בניית Gradle עצמאית.
הוא מחזיק את הקידוד, הנרמול, האחזור, האינדוקס, האופטימיזציה והבדיקות שלהם.
`SeforimLibrary/search` תלוי בו ב־API דרך composite build, ומשאיר אצלו את מתאם
ה־Hybrid, החיפוש המילולי והעימוד. האפליקציה אחראית להתקנה, פתרון שורות והעדפות.
אין תלות הפוכה ואין צורך בטוקן לחבילה פרטית. שמות ה־packages הישנים נשמרו לתאימות.

רישיון הקוד המקורי והייחוס נשמרו. משקלים, tokenizer, מסד ווקטורים אינם נכללים
במאגר החדש; זכויות השימוש בהם נפרדות מרישיון הקוד.

Powered by the technologies that drive Zayit — https://zayitapp.com/
