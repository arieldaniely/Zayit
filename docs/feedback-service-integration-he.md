# מדריך פשוט לשליחת משוב

המדריך מיועד להוספת שליחת משוב לתוכנה שלך. שירות המשוב מקבל JSON ושומר כל אירוע.
אפשר לשלוח אליו מכל שפה או פלטפורמה שיש לה גישה ל־HTTPS.

## מתי לשלוח?

שלח אירוע כשהמשתמש עושה פעולה שמלמדת משהו על תוצאת החיפוש:

- **כניסה (`entry`)** — המשתמש בוחר לפתוח תוצאת חיפוש. אל תשלח אירוע רק מפני שתוצאה הופיעה על המסך או בעת גלילה.
- **לייק (`like`)** — המשתמש מסמן במפורש שהתוצאה מועילה.
- **דיסלייק (`dislike`)** — המשתמש מסמן במפורש שהתוצאה אינה מועילה.

שלח לייק או דיסלייק רק אחרי שהמשתמש ראה את התוצאה ויכול לשפוט אותה. שלח אירוע אחד לכל פעולה מכוונת.
אם המשתמש משנה את דעתו ולוחץ על תגובה אחרת, אפשר לשלוח אירוע חדש עם אותו מזהה חיפוש וסוג תגובה מעודכן.

## לאן שולחים?

כתובת השירות:

```text
https://arielll123-meivin-api-data.hf.space/gradio_api
```

השליחה מתבצעת בשתי בקשות:

1. `POST /call/v2/save_feedback` עם `Content-Type: application/json` והגוף `{"data": {...}}`.
2. קוראים את `event_id` מתשובת ה־POST, ואז פונים אל `GET /call/save_feedback/{event_id}` ומחכים לאישור השמירה.

יש להתייחס למשוב כשמור רק כשהזרם מכיל `event: complete`, ונתוני האירוע שאחריו כוללים `success: true`.
תגובה מוצלחת לבקשת ה־POST בלבד פירושה שהשרת קיבל את המשימה, ולא בהכרח שהמשוב כבר נשמר.

## איזה מידע לשלוח?

השירות מקבל כל אובייקט JSON תקין; שמות השדות בדוגמה הם מוסכמה מומלצת, לא שדות שהשרת דורש.
שלח אובייקט JSON בתוך השדה `data`. כדאי לכלול:

- פרטי האירוע: מזהה קבוע, זמן UTC וסוג המשוב.
- פרטי התוכנה והגרסה.
- השאילתה שבוצעה, סוג החיפוש והגבלות החיפוש כגון ספרים, קטגוריות או טווח.
- הקטע המלא שבו נמצאה התוצאה, יחד עם המילים שאותרו.
- פרטי התוצאה: ספר, מזהה קטע, מיקום בתוצאות וציון הדירוג, אם זמינים.

השדות יכולים להיות מותאמים לתוכנה שלך. השדה `id` הוא מיוחד: השרת משתמש בו כשם הקובץ שבו הרשומה נשמרת.
לכן קבע אותו פעם אחת לכל אירוע, שמור אותו עם האירוע המקומי, ושלח בדיוק אותו ערך בכל ניסיון חוזר.
כך ניסיון חוזר יעדכן את אותו קובץ במקום ליצור קובץ נוסף. `eventId` יכול לקבל את אותו הערך כדי שיהיה קל להתאים בין רשומות.

## דוגמה מלאה ב־Python

הדוגמה משתמשת בספריית `requests` (`python -m pip install requests`). את הדוגמה יש להריץ ברקע,
כדי שבקשת הרשת לא תקפיא את ממשק המשתמש.

```python
import json
import requests

BASE_URL = "https://arielll123-meivin-api-data.hf.space/gradio_api"

feedback = {
    # יש ליצור ולשמור מזהה אחד לכל אירוע. בניסיון חוזר משתמשים באותו מזהה.
    "id": "a17d52b8-5779-4b4e-888f-19e504619961",
    "eventId": "a17d52b8-5779-4b4e-888f-19e504619961",
    "schemaVersion": 1,
    "occurredAt": "2026-10-06T10:30:00Z",
    "feedbackType": "like",  # entry, like, או dislike
    "software": "שם התוכנה שלך",
    "softwareVersion": "1.4.0",
    "search": {
        "searchId": "4c809abf-97f9-41cf-bc09-7bf7b861ffbe",
        "query": "דוגמה לשאילתה",
        "mode": "smart",
        "bookIds": [12, 38],
        "settings": {"baseBookOnly": True},
    },
    "modelVersion": "embedding-model-v2",
    "result": {
        "bookId": 12,
        "bookTitle": "שם הספר",
        "sectionId": "12:456",
        "paragraph": "הפסקה המלאה כפי שנשמרה במאגר.",
        "matchedParts": ["המילים שאותרו"],
        "position": 3,
        "score": 0.82,
    },
}

try:
    response = requests.post(
        f"{BASE_URL}/call/v2/save_feedback",
        json={"data": feedback},
        timeout=(10, 30),
    )
    response.raise_for_status()
    event_id = response.json()["event_id"]

    with requests.get(
        f"{BASE_URL}/call/save_feedback/{event_id}",
        stream=True,
        timeout=(10, 60),
    ) as response:
        response.raise_for_status()
        response.encoding = "utf-8"
        event_name = None
        result_data = None

        for raw_line in response.iter_lines(decode_unicode=True):
            if not raw_line:
                if event_name == "error":
                    raise RuntimeError("שרת המשוב דיווח על שגיאה")
                if event_name == "complete":
                    result = json.loads(result_data or "null")
                    if not result or result[0].get("success") is not True:
                        raise RuntimeError("השרת לא אישר את שמירת המשוב")
                    break
                event_name = None
                result_data = None
                continue

            if raw_line.startswith("event:"):
                event_name = raw_line.removeprefix("event:").strip()
            elif raw_line.startswith("data:"):
                result_data = raw_line.removeprefix("data:").strip()
        else:
            raise RuntimeError("הזרם הסתיים ללא אישור שמירה")

except (requests.RequestException, ValueError, KeyError, RuntimeError) as error:
    # יש לשמור את feedback באחסון המקומי ולנסות שוב מאוחר יותר.
    # אין ליצור id חדש בניסיון חוזר. מחקו את האירוע מהתור רק אחרי success: true.
    print(f"משוב ממתין לשליחה חוזרת: {error}")
```

## מה עושים כשאין אינטרנט?

אין להשאיר את ממשק המשתמש בהמתנה לרשת או להציג כישלון כאילו הפעולה בתוכנה נכשלה.
שמור את אובייקט המשוב, כולל `id` וזמן האירוע המקורי, בקובץ או במסד נתונים מקומי.
נסה שוב בעליית התוכנה ומדי כמה דקות. מחק את האירוע המקומי רק אחרי שקיבלת אישור `success: true`.
כדי להגביל את גודל התור, מחק אירועים שלא נשלחו אחרי חודשיים.

## בדיקת חיבור מהירה עם cURL

ה־POST בלבד בודק שהשרת קיבל את המשימה. כדי לוודא שמירה מלאה, צריך גם לבצע את בקשת ה־GET
ולחכות ל־`event: complete` ול־`success: true`, כפי שמוצג בדוגמת Python.

```bash
curl -X POST \
  "https://arielll123-meivin-api-data.hf.space/gradio_api/call/v2/save_feedback" \
  -H "Content-Type: application/json" \
  -d '{"data":{"id":"בדיקת-חיבור-חד-פעמית","feedbackType":"entry","software":"בדיקה"}}'
```

לבדיקה אמיתית של ה־POST וה־GET, השתמש באותו `event_id` שיוחזר בתשובה:

```bash
curl -N \
  "https://arielll123-meivin-api-data.hf.space/gradio_api/call/save_feedback/EVENT_ID"
```

ה־cURL לדוגמה כותב רשומה למאגר. אל תריץ אותו עם מידע של משתמש אמיתי לצורך בדיקה.
