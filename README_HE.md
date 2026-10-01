# זיתא — יישום עצמאי ללימוד ספרי קודש

[English](README.MD)

זיתא היא פורק עצמאי של [זית](https://github.com/kdroidFilter/Zayit), לקריאה,
לחיפוש וללימוד של ספרי קודש ב־Windows, ב־macOS ובלינוקס. לזיתא שם מוצר,
מזהי יישום, נתיבי התקנה, מנגנון עדכון וקובצי הפצה נפרדים. זיתא אינה נוצרת,
מופצת, מאושרת או נתמכת על ידי מפתחי זית.

## מצב ההפצה

התוכנה והאתר מוגדרים לקבל את הפצות זיתא מן המאגר
[`arieldaniely/Zayit`](https://github.com/arieldaniely/Zayit). אייקון זיתא הייחודי
מוטמע בחבילות שולחן העבודה ובאתר. לפני הפצה ציבורית נדרשים עוד בניית Release
נקייה ובדיקת התקנה על מערכות היעד; חתימת קוד מומלצת אך אינה תנאי טכני ליצירת הקבצים.

מסד הספרים מורד בנפרד מן ההפצה המקורית של
[`kdroidFilter/SeforimLibrary`](https://github.com/kdroidFilter/SeforimLibrary),
ואינו מוצג כנכס שנוצר או מופץ על ידי פרויקט זיתא.

## בנייה ובדיקות

יש להשתמש ב־JBR/JDK 25 ולשכפל את תתי־המודולים:

```bash
git clone --recurse-submodules https://github.com/arieldaniely/Zayit.git
./gradlew :SeforimApp:jvmTest
./gradlew :SeforimApp:run
```

האתר נמצא בתיקייה `website/`:

```bash
cd website
npm install
npm run build
```

## קישורים ומזהים

- מזהה היישום: `io.github.arieldaniely.zayita`
- מזהה החבילה ב־macOS: `io.github.arieldaniely.zayita.desktopApp`
- שם החבילה/הקובץ בשולחן העבודה: `zayita`
- פרוטוקול קישורים מקורי: `zayita://`
- תאימות לקישורי קלט ישנים: `zayit://` ו־`otzaria://`

## גופנים

הגופן רש״י אמיתי, שניתן ברישיון בלעדי לזית, אינו כלול. זיתא משתמשת ב־
[Mekorot Rashi](https://github.com/aharonium/fonts/tree/master/Fonts/Hebrew%20Letters%20with%20Vowels%20%28no%20cantillation%29/Mekorot%20%28LPPL%29/Rashi)
תחת LPPL; הודעת הרישיון מצורפת למשאבי היישום.

## רישיון וייחוס

קוד היישום מופץ תחת GNU AGPL v3 ובכפוף לתנאי הייחוס הנוסף בסעיף 7(b)
שבקובץ [LICENSE](LICENSE). לכל ספר או מאגר עשויים להיות תנאים נפרדים; ראו
[CONDITIONS.md](SeforimApp/src/commonMain/composeResources/files/CONDITIONS.md).

> Powered by the technologies that drive Zayit — https://zayitapp.com/
