# חיפוש חכם עם Meivin Round 2

בחיפוש טקסט יש שלושה מצבים: **מדויק** (מילות השאילתה בלי הרחבת מילון, n-grams או fuzzy), **גמיש** (החיפוש הקיים), ו־**חכם** (מיזוג RRF של החיפוש הגמיש עם KNN סמנטי). בחירת המצב נשמרת לכל לשונית חיפוש. החיפוש הרגיל אינו דורש מודל או אינדקס סמנטי.

## חבילת ההפצה

האפליקציה מורידה חבילה שפורסמה ב־GitHub Releases של `arieldaniely/Zayit`, או מייבאת את כל חלקי ה־ZIP שלה מקבצים מקומיים. החבילה כוללת את `seforim-embed-round2-int8.onnx`, את `tokenizer.json` התואם ואת כל חלקי אינדקס Lucene. אין הורדה מ־Hugging Face באפליקציה. הקבצים מותקנים יחד תחת `<seforim.db>.semantic/` כדי שהחיפוש המילולי הבסיסי יישאר זמין ללא התקנתם. בהורדה האפליקציה קוראת תחילה את `semantic-bundle.json` הקטן ובוחרת רק Release שחתימת מסד הנתונים שלו תואמת למסד המותקן.

לפני התקנה האפליקציה בודקת את חתימות SHA-256 של המודל והטוקנייזר, את נוכחות כל חלקי האינדקס, את ממד הווקטור (256) ואת SHA-256 של מסד הנתונים שממנו נבנה האינדקס. חבילה של גרסת מסד נתונים אחרת לא תותקן. יש לבחור **את כל** קובצי `semantic-bundle-part-XX.zip` יחד בייבוא מקומי.

## יצירת חבילה ב־GitHub Actions

ה־workflow [`build-semantic-index.yaml`](../.github/workflows/build-semantic-index.yaml) מופעל ידנית. נדרש secret בשם `HF_TOKEN` עם גישה שאושרה למאגר המודל, לצורך *הבנייה בלבד*. ה־workflow מקבע גרסת מאגר נתונים מתוך `kdroidFilter/SeforimLibrary`, מוריד את מודל Round 2, בודק את חתימות הקבצים, ובונה 16 חלקי אינדקס במקביל. כל חלק הוא אינדקס Lucene עצמאי לשורות שמזהיהן שייכים לחלק; החלק הראשון מכיל גם את המודל והטוקנייזר. הפרסום נשאר טיוטה עד שכל 16 החלקים ו־`semantic-bundle.json` עלו.

על פי [תיעוד ה־runners של GitHub](https://docs.github.com/en/actions/reference/runners/github-hosted-runners), שימוש ב־standard runners של מאגר ציבורי הוא בחינם ו־runner של Ubuntu מספק 4 CPU,‏ 16 GB RAM ו־14 GB אחסון. [מגבלת הזמן לכל job](https://docs.github.com/en/actions/reference/limits) היא שש שעות, ו[קובץ Release](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases) מוגבל ל־2 GiB. לכן מספר החלקים נבחר ל־16, וכל חלק נבדק לפני העלאה. עדיין צריך להריץ את ה־workflow על מאגר הייצור ולמדוד זמן, גודל דיסק וכיסוי. אם חלק מסוים חורג ממגבלת הזמן או הדיסק, אפשר לבנות את אותו חלק על מכונת Kaggle באמצעות משימת Gradle שלהלן ולצרף אותו לטיוטת ה־Release לפני פרסום; יש להשתמש באותו מסד נתונים, מודל, טוקנייזר ומספר חלקים.

```bash
./gradlew -p SeforimLibrary :search:buildSemanticIndex \
  -PseforimDb=/path/to/seforim.db \
  -PsemanticModelDir=/path/to/round2-model \
  -PsemanticIndexDir=/path/to/output \
  -PshardIndex=0 -PshardCount=16
```

המודל דורש גישה מאושרת ב־Hugging Face *רק בסביבת הבנייה*. ה־workflow מפרסם עותק של המודל כחלק מהחבילה; לפני הפעלתו יש לוודא שההפצה תואמת למדיניות הפרסום הרצויה ולרישיון המודל (CC BY-NC-SA 4.0).

## חוזה הקידוד

המודל מקבל `input_ids` ו־`attention_mask` מסוג int64. לפני הטוקניזציה מתבצע נרמול Round 2, ולשאילתה נוסף `[QUERY]` ולשורת מקור `[PASSAGE]`. הקלט נחתך ל־256 טוקנים. פלט ONNX הוא וקטור float32 מנורמל של 256 ממדים. גם בניית האינדקס וגם חיפוש בזמן ריצה משתמשים באותו `SeforimEmbedder` ובאותו טוקנייזר.

האינדקס משתמש ב־`line_id` מתוך מסד הנתונים שנארז להפצה. יש למדוד בנפרד את איכות האחזור על שורות Zayit; בדיקת תאימות מספרית של מודל ONNX אינה מדידת איכות חיפוש.
