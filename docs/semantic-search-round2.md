# חיפוש חכם עם Meivin Round 2

בחיפוש טקסט יש שלושה מצבים: **מדויק** (מילות השאילתה בלי הרחבת מילון, n-grams או fuzzy), **גמיש** (החיפוש הקיים), ו־**חכם** (מיזוג RRF של החיפוש הגמיש עם KNN סמנטי). בחירת המצב נשמרת לכל לשונית חיפוש. החיפוש הרגיל אינו דורש מודל או אינדקס סמנטי.

## חבילת ההפצה

האפליקציה מורידה חבילה שפורסמה ב־GitHub Releases של `arieldaniely/Zayit`, או מייבאת `semantic-bundle.tar.zst` מקומי (או את כל קובצי `semantic-bundle.tar.zst.partXX` כאשר החבילה פוצלה). החבילה כוללת את `seforim-embed-round2-int8.onnx`, את `tokenizer.json` התואם ואת כל חלקי אינדקס Lucene. אין הורדה מ־Hugging Face באפליקציה. הקבצים מותקנים יחד תחת `<seforim.db>.semantic/` כדי שהחיפוש המילולי הבסיסי יישאר זמין ללא התקנתם. בהורדה האפליקציה קוראת תחילה את `semantic-bundle.json` הקטן ובוחרת רק Release שחתימת מסד הנתונים שלו תואמת למסד המותקן. חבילה בטיוטת Release אינה מופיעה להורדה הציבורית באפליקציה; יש להוריד אותה ידנית מתוך GitHub כשמחוברים לחשבון בעל גישה, ואז לייבא את חלקיה. האפליקציה עדיין מקבלת חבילות ZIP שנוצרו על ידי ה־workflow הקודם.

לפני התקנה האפליקציה בודקת את חתימות SHA-256 של המודל והטוקנייזר, את נוכחות כל חלקי האינדקס, את ממד הווקטור (256) ואת SHA-256 של מסד הנתונים שממנו נבנה האינדקס. חבילה של גרסת מסד נתונים אחרת לא תותקן. בחבילה מפוצלת יש לבחור **את כל** החלקים יחד בייבוא מקומי; אין לבחור את `semantic-bundle.json`.

## יצירה מקומית ממסד הנתונים המותקן

כדי שהאינדקס יתאים בדיוק למסד הנתונים במחשב, השתמשו ב־`seforim.db` שמותקן ב־Zayit. ב־Windows מיקומו בדרך כלל `%APPDATA%\io.github.kdroidfilter.seforimapp\databases\seforim.db`. הסקריפט בונה שמונה חלקי אינדקס באופן סדרתי, משתמש בשמונה עובדים בכל חלק, ומדלג על חלקים שהושלמו אם מריצים אותו שוב. התהליך עשוי לקחת שעות רבות ומשתמש במעבד ובשטח דיסק משמעותיים. רצוי לסגור את Zayit ולא לשנות את מסד הנתונים בזמן הבנייה.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
.\scripts\build-local-semantic-index.ps1 `
  -Database "$env:APPDATA\io.github.kdroidfilter.seforimapp\databases\seforim.db" `
  -ModelDirectory 'C:\path\to\zayit-round2-onnx' `
  -OutputDirectory '.semantic-local-output\index'
```

לאחר השלמת כל החלקים, משימת האריזה מוודאת את האינדקס מול המסד והמודל, ויוצרת קובץ zstd יחיד אם גודלו עד כ־1.9 GiB; אם הוא גדול יותר, היא יוצרת חלקים של אותו זרם דחוס. `semantic-bundle.json` מיועד לפרסום ב־Release ולבחירת החבילה הנכונה להורדה, אך אינו נחוץ בייבוא המקומי.

```powershell
cd SeforimLibrary
.\gradlew.bat :packaging:packageSemanticBundle `
  "-PseforimDb=$env:APPDATA\io.github.kdroidfilter.seforimapp\databases\seforim.db" `
  '-PsemanticModelDir=C:\path\to\zayit-round2-onnx' `
  '-PsemanticIndexDir=..\.semantic-local-output\index' `
  '-PsemanticBundleOutput=..\.semantic-local-output\semantic-bundle.tar.zst'
```

אפשר להעלות את הקובץ או את כל חלקיו יחד עם `semantic-bundle.json` ל־Release טיוטה של Zayit. אם רוצים שהוא יהיה זמין להורדה מתוך האפליקציה לכל משתמש עם מסד תואם, יש לפרסם את ה־Release.

## בנייה ב־Kaggle עם שני T4

המחברת [`build_semantic_index_kaggle_t4x2.ipynb`](../notebooks/build_semantic_index_kaggle_t4x2.ipynb) מורידה את `seforim_bundle.tar.zst.part01/02` מ־[גרסת v2-20260814115718](https://github.com/arieldaniely/SeforimLibrary/releases/tag/v2-20260814115718), בודקת את חתימות הקבצים שפורסמו, ומחלצת את `seforim.db`. היא מקודדת את שורות המסד באצוות על שני T4, בונה שמונה חלקי Lucene מקובצי הווקטורים, ומפיקה את חבילת `tar.zst` עם המודל והטוקנייזר. צריך להפעיל Internet ולבחור GPU T4 x2 בהגדרות המחברת, ולהגדיר את `HF_TOKEN` ב־Kaggle Secrets עם גישה מאושרת למאגרי המודל. מודל ה־ONNX אינו יורד מה־Hugging Face באפליקציה עצמה.

החבילה שתיווצר תתאים למסד של ה־Release הזה בלבד. אם ה־DB המותקן במחשב שונה, יש להתקין את אותה גרסת מסד או לבנות אינדקס מהמסד המותקן. ל־Kaggle מגבלת הרצה של 12 שעות ומגבלת פלט נשמר של 20 GiB; המחברת שומרת את נתוני הביניים בתיקיית scratch ומותירה בתיקיית הפלט רק את קובצי ההפצה. זמן הבנייה בפועל יימדד רק בהרצה מלאה.

## יצירת חבילה ב־GitHub Actions

ה־workflow [`build-semantic-index.yaml`](../.github/workflows/build-semantic-index.yaml) מקבל את התג של **טיוטת** Release ב־`arieldaniely/SeforimLibrary`. הוא מוריד את מסד הנתונים מהטיוטה, בודק את חתימות קובצי מודל Round 2, ובונה 16 חלקי אינדקס במקביל. כל חלק הוא אינדקס Lucene עצמאי לשורות שמזהיהן שייכים לחלק; החלק הראשון מכיל גם את המודל והטוקנייזר. התוצאה עולה ל־Release חדש ב־`arieldaniely/Zayit` שנשאר **טיוטה גם אחרי שכל החלקים הושלמו**. ה־workflow מוודא את נוכחות 16 החלקים ואת `semantic-bundle.json` ואינו מפרסם אותם לציבור.

כדי לבנות חבילה פרטית לבדיקה:

1. ודאו שלטיוטת מסד הנתונים יש תג ושקובצי `seforim_bundle.tar.zst.part*` מצורפים אליה.
2. הגדירו ב־`arieldaniely/Zayit` שני Actions secrets: ‏`HF_TOKEN` עם גישה מאושרת למודל, ו־`SEFORIM_LIBRARY_TOKEN` — fine-grained GitHub token של משתמש שרשאי לראות את טיוטת `arieldaniely/SeforimLibrary`, עם הרשאת Contents: read למאגר זה. `GITHUB_TOKEN` של Zayit אינו נותן גישה אוטומטית למאגר השני.
3. אחרי שקובץ ה־workflow נמצא בענף ברירת המחדל של Zayit, פתחו Actions → Build optional Round 2 semantic index → Run workflow, הזינו `db_release_tag` כתג הטיוטה, והפעילו. GitHub דורש שקובץ workflow להפעלה ידנית יהיה בענף ברירת המחדל; אפשר לבחור את ענף הקוד הרצוי בתפריט ההפעלה.
4. בסיום מוצלח, פתחו את טיוטת `semantic-round2-*` החדשה ב־Releases של Zayit. הורידו את כל קובצי `semantic-bundle-part-XX.zip` וייבאו אותם יחד מתוך חלונית התקנת החיפוש החכם באפליקציה. אין לבחור את `semantic-bundle.json` לייבוא. השאירו את שתי ה־Releases כטיוטות כל עוד החבילה מיועדת רק לבעלי גישת כתיבה למאגרים.

[תיעוד GitHub על נראות טיוטות Release](https://docs.github.com/en/rest/releases/releases) קובע שרק משתמשים בעלי גישת push מקבלים אותן ברשימות ה־API. [הפעלה ידנית של workflow](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow) דורשת שקובץ ה־workflow יהיה בענף ברירת המחדל.

על פי [תיעוד ה־runners של GitHub](https://docs.github.com/en/actions/reference/runners/github-hosted-runners), שימוש ב־standard runners של מאגר ציבורי הוא בחינם ו־runner של Ubuntu מספק 4 CPU,‏ 16 GB RAM ו־14 GB אחסון. [מגבלת הזמן לכל job](https://docs.github.com/en/actions/reference/limits) היא שש שעות, ו[קובץ Release](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases) מוגבל ל־2 GiB. לכן מספר החלקים נבחר ל־16, וכל חלק נבדק לפני העלאה. עדיין צריך להריץ את ה־workflow על מסד הנתונים המלא ולמדוד זמן, גודל דיסק וכיסוי. אם חלק מסוים חורג ממגבלת הזמן או הדיסק, אפשר לבנות את אותו חלק על מכונת Kaggle באמצעות משימת Gradle שלהלן ולצרף אותו לטיוטת ה־Release; יש להשתמש באותו מסד נתונים, מודל, טוקנייזר ומספר חלקים.

```bash
./gradlew -p SeforimLibrary :search:buildSemanticIndex \
  -PseforimDb=/path/to/seforim.db \
  -PsemanticModelDir=/path/to/round2-model \
  -PsemanticIndexDir=/path/to/output \
  -PshardIndex=0 -PshardCount=16
```

המודל דורש גישה מאושרת ב־Hugging Face *רק בסביבת הבנייה*. ה־workflow אורז עותק של המודל בתוך הטיוטה; לפני פרסום עתידי לציבור יש לוודא שההפצה תואמת למדיניות הפרסום הרצויה ולרישיון המודל (CC BY-NC-SA 4.0).

## חוזה הקידוד

המודל מקבל `input_ids` ו־`attention_mask` מסוג int64. לפני הטוקניזציה מתבצע נרמול Round 2, ולשאילתה נוסף `[QUERY]` ולשורת מקור `[PASSAGE]`. הקלט נחתך ל־256 טוקנים. פלט ONNX הוא וקטור float32 מנורמל של 256 ממדים. גם בניית האינדקס וגם חיפוש בזמן ריצה משתמשים באותו `SeforimEmbedder` ובאותו טוקנייזר.

האינדקס משתמש ב־`line_id` מתוך מסד הנתונים שנארז להפצה. יש למדוד בנפרד את איכות האחזור על שורות Zayit; בדיקת תאימות מספרית של מודל ONNX אינה מדידת איכות חיפוש.
