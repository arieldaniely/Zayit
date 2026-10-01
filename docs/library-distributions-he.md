# חבילות התקנה של זיתא

הורדת מסד הספרים היא מתוך `arieldaniely/SeforimLibrary`. ההתקנה המקוונת בוחרת את
`seforim_bundle.tar.zst` (או את כל חלקיו), שהיא החבילה המלאה. בייבוא מקומי מותקנים
בדיוק הרכיבים שנמצאים בחבילה שנבחרה. רכיבים חסרים מוצעים בנפרד וניתן לדלג עליהם.

## הגבלות הפצה של המודל והווקטורים

חל איסור מוחלט להפיץ מחדש את מודל הבינה המלאכותית (AI) ואת הווקטורים המופצים עם התוכנה או בחבילות ההשלמה שלה, כולם או חלקם, בכל דרך, לרבות כחלק מחבילה אחרת או כשהם משולבים במוצר אחר, ללא קבלת אישור מראש ובכתב מ־arieldaniely@gmail.com. רישיון קוד התוכנה אינו מעניק רשות להפיץ מחדש רכיבים אלה.

## יצירת שש החבילות

מתוך שורש הפרויקט:

```powershell
./scripts/package-library-distributions.ps1 -Database C:/library/seforim.db `
  -PdfDirectory 'C:/library/תלמוד בבלי' `
  -SemanticDirectory C:/library/seforim.db.semantic -OutputDirectory C:/releases
```

לצד המסד צריכים להיות הקטלוג, אינדקסי הטקסט וקובצי המידע הקיימים. תיקיית הווקטורים
כוללת `model` ו־`index` של Round 2, התואמים לאותו מסד. ברירת המחדל של
`:packaging:packageArtifacts` כוללת PDF ווקטורים; חסר ברכיבים אלה גורם לשגיאה כדי שלא
תיווצר בטעות חבילה מלאה שחסרים בה רכיבים. ליצירת גרסה חלקית משתמשים ב־
`-PincludePdf=false` ו/או `-PincludeVectors=false`.

בנוסף נוצרות חבילות השלמה נפרדות ל־PDF ולווקטורים, ומניפסט לווקטורים.

התוצרים הם חבילה מלאה, חבילה ללא PDF, חבילה ללא ווקטורים וחבילת מסד בלבד.
כל אחת היא זרם tar.zst אחד שמפוצל, אם צריך, לחלקים בגודל שמתאים ל־GitHub.
אם נוצרו חלקים, מעלים את החלקים בלבד; אחרת מעלים את קובץ tar.zst.

## מסלול מהיר: הכול במחברת Kaggle

המחברת `notebooks/build_semantic_index_kaggle_t4x2.ipynb` מבצעת כעת את כל ההמשך
אחרי בניית הבסיס ב־GitHub Actions: הורדה ואימות הבסיס כולל אינדקס מילולי,
יצירת הווקטורים בשני GPU ובניית אינדקס סמנטי אחד מאוחד, הורדת PDF מאוצריא, אריזת שש חבילות והעלאתן.
היא מפרסמת אוטומטית רק לאחר השלמת כל החבילות.

לפני ההרצה:

1. העלו את המחברת המעודכנת לקאגל. כלי ההפצה וקוד האריזה כלולים בתא
   `release-tools`, ולכן אין צורך לפרסם את שינוייהם בגיטהאב לפני ההרצה.
   קוד בניית האינדקס המאוחד כלול גם הוא בתא; בחרו `ZAYIT_REF` עם תמיכת int8.
2. הגדירו שני T4 ו־Internet. הוסיפו ב־Kaggle Secrets את `HF_TOKEN` עם גישה
   למודל ואת `GH_TOKEN` עם הרשאות Contents: read and write לשני המאגרים
   `arieldaniely/SeforimLibrary` ו־`arieldaniely/Zayit`, ואפשרו למחברת להשתמש בהם.
3. לאחר סיום בניית המסד הזינו בתא הבנייה את `DB_RELEASE_TAG`. אין צורך לשנות
   את בניית המסד שכבר רצה. היא יכולה להפיק Pre-release כרגיל.
4. הפעילו Save & Run All. `PDF_RELEASE_TAG` ריק בוחר את גרסת ה־PDF האחרונה
   מ־Otzaria/otzaria-library ומתעד את התג והחתימה; אפשר להזין תג מפורש.

כל חבילה עולה לטיוטת `<DB_RELEASE_TAG>-full` ונמחקת מהדיסק לאחר העלאה מאומתת,
כדי שלא להחזיק את שש החבילות יחד או לשמור אותן בפלט Kaggle. קובצי הבסיס,
ה־PDF והאינדקס נשארים בדיסק העבודה לצורך אריזת החבילה הבאה. בסוף הטיוטה
מתפרסמת ומסומנת Latest. השלמת הווקטורים מתפרסמת גם ב־Zayit בהתאם למנגנון
ההורדה הקיים. פלט המחברת כולל manifest, checksums וקישור להפצה.

אין עדיין מדידה של זמן או נפח דיסק להרצה מלאה. אם ההרצה נכשלת לפני שלב
הפרסום, התוצרים שהועלו נשארים בטיוטה; גרסת הבסיס אינה מוחלפת. בהרצה חוזרת
באותה סביבת עבודה קובצי האינדקס התואמים ניתנים לשימוש חוזר. התחלת session
חדש אינה משמרת את קובצי ה־scratch. כדי לשנות מקור מסד יש לבחור תיקיית עבודה חדשה.

## חלופה: אריזה ופרסום מהמחשב

1. הפעילו ב־SeforimLibrary את `Manual Generate + Pre-Release` עם `prerelease=true`.
   ה־workflow הקיים בונה מסד ואינדקס מילולי ואורז בסיס ללא PDF וללא וקטורים.
   רשמו את התג שנוצר. השאירו את הבסיס כ־Pre-release בזמן הכנת ההפצה המלאה.
2. פרסמו את שינויי הסקריפטים והמחברת בענף שממנו Kaggle מושך את הקוד.
   העלו לקאגל את `notebooks/build_semantic_index_kaggle_t4x2.ipynb`, הזינו
   `DB_RELEASE_TAG`, בחרו שני T4, הפעילו Internet והגדירו `HF_TOKEN`.
   לחלופה זו יש להסיר `--all-distributions` מהפקודה בתא הבנייה.
   המחברת מזהה את כל חלקי הבסיס ומאמתת את חתימותיהם. תוצריה הם החבילה
   הסמנטית וה־manifest; האריזה של כל שש ההפצות מתבצעת במחשב.
3. הורידו את כל חלקי הבסיס, את תוצרי Kaggle ואת חבילת PDF מאוצריא למחשב.
   נדרשים Python עם `zstandard`, JDK 25 ו־GitHub CLI (`gh auth login`).
   מקור ה־PDF הוא `Otzaria/otzaria-library`, asset בשם `talmud_bavli_latest.tar.zst`.
   יש לבחור תג מפורש של גרסת המקור כדי שאפשר יהיה לשחזר את ההפצה.

```powershell
python -m pip install zstandard
gh release download <BASE_TAG> --repo arieldaniely/SeforimLibrary --pattern 'seforim_bundle.tar.zst*' --dir .release-input/base
gh release download <PDF_TAG> --repo Otzaria/otzaria-library --pattern talmud_bavli_latest.tar.zst --dir .release-input/pdf

# בחרו את part01 אם יש חלקים, או את tar.zst אם יש קובץ יחיד.
python scripts/extract-library-archive.py .release-input/base/seforim_bundle.tar.zst.part01 .release-input/library
python scripts/extract-library-archive.py .release-input/pdf/talmud_bavli_latest.tar.zst .release-input/pdf-extracted
python scripts/extract-library-archive.py .release-input/semantic/semantic-bundle.tar.zst.part01 .release-input/library/seforim.db.semantic

./scripts/package-library-distributions.ps1 `
  -Database .release-input/library/seforim.db `
  -PdfDirectory '.release-input/pdf-extracted/תלמוד בבלי' `
  -SemanticDirectory .release-input/library/seforim.db.semantic `
  -OutputDirectory .release-output
```

הפלט חייב להיות בתיקייה ריקה. רמת הדחיסה היא 6 כברירת מחדל לקיצור זמן האריזה;
ניתן להעביר `-CompressionLevel 22` לדחיסה חזקה יותר. האריזה אינה משכתבת
`release_info.txt` ואינה מורידה מחדש `lexical.db`. הסקריפט מפיק `distributions.json`
ורשימת checksums של הקבצים המיועדים להעלאה.

| תוכן | שם הארכיון |
|---|---|
| בסיס + PDF + סמנטי, ברירת המחדל | `seforim_bundle.tar.zst` |
| בסיס + סמנטי | `seforim_bundle-no-pdf.tar.zst` |
| בסיס + PDF | `seforim_bundle-no-vectors.tar.zst` |
| בסיס | `seforim_bundle-database-only.tar.zst` |
| PDF בלבד | `talmud_bavli_latest.tar.zst` |
| וקטורים + מודל + טוקנייזר בלבד | `semantic-bundle.tar.zst` |

4. העלו ל־Release חדש עם תג ייחודי, למשל `<BASE_TAG>-full`, כדי לשמור את
   חבילת הבסיס המקורית. הסקריפט מעלה את שש החבילות ל־SeforimLibrary ואת
   ההשלמה הסמנטית גם ל־Zayit, שם האפליקציה מחפשת אותה כיום.

```powershell
./scripts/publish-library-distributions.ps1 -OutputDirectory .release-output -ReleaseTag '<BASE_TAG>-full' -Publish
```

בלי `-Publish`, שתי הגרסאות נשארות Draft. עם הדגל, ההפצה המלאה מסומנת Latest.
יש לרענן לאחר הפרסום את `release-manifest.json` באמצעות workflow העדכון הקיים.
קובץ PDF ההשלמה חייב להיות קטן מ־2 GiB; הורדת PDF הנוכחית באפליקציה מצפה
לקובץ יחיד. שאר הארכיונים יכולים להיות מפוצלים.

## מקורות להשלמת רכיבים

להשלמת PDF יש לפרסם גם `talmud_bavli_latest.tar.zst` בגרסה האחרונה של
`arieldaniely/SeforimLibrary`. להשלמת ווקטורים משמשות גרסאות `semantic-round2-*`
ב־`arieldaniely/Zayit`, עם `semantic-bundle.json` והארכיונים שהמניפסט מתאר.
המתקין בודק את טביעת המסד ואת תקינות הווקטורים לפני התקנתם.

בחירת מקום ההתקנה מתייחסת לשורש. המסד וכל הרכיבים נמצאים ב־`databases` שבתוכו.
אפשר להתקין רכיבים שדולגו דרך הגדרות הנתונים בתוכנה.
