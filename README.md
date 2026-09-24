# Foodio AI 🥗

**Healthy eating for Africa, starting with Uganda.** Photograph your plate before you eat, or just describe it. Foodio identifies each food (matooke, posho, beans, binyebwa, dodo, luwombo, rolex…), estimates portions and calories, and checks the meal against your health conditions, diet plan and weight goal. It then shows you how to re-portion it into a balanced plate.

Runs on **iOS, Android and the web** (phone, tablet and desktop) from one Expo / React Native codebase.

## Features

| | |
|---|---|
| 📸 **Meal scan** | Take or upload a photo. Foodio identifies each food, estimates grams and plate share, and calculates calories, carbs, protein, fat, fibre and sodium. |
| ❤️ **Health-aware verdict** | "Good for you", "Eat with care" or "Not recommended", based on your profile: diabetes, pre-diabetes, high blood pressure, high cholesterol, kidney disease, pregnancy, anaemia, weight loss, ulcers, gout, coeliac disease, and allergies (groundnuts, gluten, fish, eggs, dairy). |
| ❓ **"Can I eat this?"** | No photo needed. Describe the meal ("2 chapatis and tea with sugar") and get the same check before you eat. |
| 💬 **Clarifying questions** | When Foodio is unsure (which greens? fried or stewed?), it asks short multiple-choice questions and re-analyses with your answers. |
| 🧂 **Invisible ingredients** | Salt, stock cubes, cooking oil, sugar and groundnut paste can't be seen in a photo. Foodio asks about the ones that matter for *you* (salt for high blood pressure, sugar for diabetes or keto, oil for weight loss, hidden groundnuts for allergies). Your answers are added to the numbers. |
| ⚖️ **Weight goals** | Set a target such as "lose 5 kg in 1 month". Foodio checks the pace is safe (0.5–1 kg a week, and never below 1,200/1,500 kcal a day), tells you the realistic date if the target is too fast, sets a daily calorie budget, and checks every meal against what's left today. Weigh-ins are charted against the plan. |
| 🥑 **Diet plans** | Keto, low-carb, intermittent fasting (16:8, 18:6, 14:10, 20:4 with your own eating window), Mediterranean, DASH, vegetarian, vegan and high-protein. Each meal is checked against your plans, e.g. net carbs for keto, or "you're still in your fasting window until 12:00". |
| 🎗️ **Cancer care** | For people in treatment or recovery: focus on protein and energy, food-safety warnings when immunity is low, gentle-food tips for nausea or mouth sores, limits on red/charred/processed meat, and no weight-loss deficit during treatment unless the care team agrees. |
| 🧑🏾‍⚕️ **Foodio coach** | "What should I eat next?" Looks at everything you've eaten today and your remaining budget, and suggests local meals that fit. |
| 🍽️ **Plate picture** | Your plate next to a healthy plate for your condition, plus what's missing and how to re-portion the meal. |
| 👩🏾‍🍳 **Foodio Kitchen** | Enter the ingredients you have, or a dish you want to cook, and get 2–3 balanced local recipes with steps and a plating diagram. |
| 📚 **African food guide** | 50+ Ugandan and African foods with local names, nutrition per serving, glycaemic index and healthier-cooking tips. |
| 📈 **Progress** | Weight-goal tracker, weigh-in chart, today's calorie budget, last 7 days vs. budget, and meal history. All stored on the device only. |

## How it works

```
 app/ (Expo: iOS · Android · Web)
   │  photo (resized to 1024 px) + health profile
   ▼
 server/ (Node + Express)
   ├─ ml/serve.py  (optional) ── your trained African-food classifier → top-5 hints
   ├─ Claude (vision, structured output) ── identify foods, grams, questions, advice
   └─ shared/ nutrition rules ── nutrients from the Foodio food DB, balance score,
                                 condition/allergy checks (can only make the verdict stricter)
```

- **`shared/`**: the Ugandan/African food database (`foods.ts`), nutrition maths plus health and diet-plan rules (`nutrition.ts`), weight-goal planning (`weight.ts`), and types. The app and the server use the same code, so the numbers always match.
- **`server/`**: the API. The Anthropic key stays here and is never shipped in the app.
- **`app/`**: the Expo Router app (`src/app/` holds the screens).
- **`ml/`**: data preparation, training and serving for Foodio's own food-image classifier (Kaggle African food data plus Ugandan photos). See [ml/README.md](ml/README.md).

The AI only identifies foods, estimates portions and asks about hidden ingredients. Wherever a food matches the database, the calories and nutrients come from the database. The rule-based checks (e.g. "over 90 g carbs for a diabetic", "contains groundnuts", "breaks ketosis", "inside your fasting window", "over today's calorie budget") can override a softer AI verdict.

API routes: `POST /api/analyze` (photo and/or description), `POST /api/recipes`, `POST /api/coach`, `GET /api/foods`.

## Getting started

Requires Node 20+.

### 1. API server
```bash
cd server
npm install
cp .env.example .env        # add your ANTHROPIC_API_KEY
npm run dev                 # http://localhost:8787
npm test                    # nutrition and health-rule tests
```

### 2. App
```bash
cd app
npm install
cp .env.example .env        # EXPO_PUBLIC_API_URL=http://<your-computer-ip>:8787 for phones
npx expo start              # press w for web, or scan the QR code with Expo Go
```

### 3. Build for Android and iOS
Uses [EAS Build](https://docs.expo.dev/build/introduction/). Set your deployed API URL in `app/eas.json` first.
```bash
cd app
npx eas-cli@latest build --platform android --profile preview   # installable APK
npx eas-cli@latest build --platform ios --profile production    # App Store build (needs an Apple developer account)
npx expo export --platform web                                  # static web app in dist/
```

## Notes

- **Nutrition values** in `shared/foods.ts` are approximations for foods as typically served. Refine them against the Uganda Food Composition Table before any clinical use.
- **Privacy**: the health profile and meal history stay on the device. A photo and the profile are sent to the server only when you analyse a meal.
- **Not medical advice**: Foodio supports healthy eating and does not replace a doctor, dietitian or health worker.
