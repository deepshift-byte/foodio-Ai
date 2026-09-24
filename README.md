# Foodio AI 🥗

**Healthy eating for Africa, starting with Uganda.** Photograph your plate before you eat. Foodio identifies each food (matooke, posho, beans, binyebwa, dodo, luwombo, rolex…), estimates portions and calories, checks the meal against your health conditions, and shows you how to re-portion it into a balanced plate.

Runs on **iOS, Android and the web** (phone, tablet and desktop) from one Expo / React Native codebase.

## Features

| | |
|---|---|
| 📸 **Meal scan** | Take or upload a photo. Foodio identifies each food, estimates grams and plate share, and calculates calories, carbs, protein, fat, fibre and sodium. |
| ❤️ **Health-aware verdict** | "Good for you", "Eat with care" or "Not recommended", based on your profile: diabetes, pre-diabetes, high blood pressure, high cholesterol, kidney disease, pregnancy, anaemia, weight loss, ulcers, gout, coeliac disease, and allergies (groundnuts, gluten, fish, eggs, dairy). |
| 💬 **Clarifying questions** | When Foodio is unsure (which greens? fried or stewed? sugar in the tea?), it asks short multiple-choice questions and re-analyses with your answers. |
| 🍽️ **Plate picture** | Your plate next to a healthy plate for your condition, plus what's missing and how to re-portion the meal. |
| 👩🏾‍🍳 **Foodio Kitchen** | Enter the ingredients you have, or a dish you want to cook, and get 2–3 balanced local recipes with steps and a plating diagram. |
| 📚 **African food guide** | 50+ Ugandan and African foods with local names, nutrition per serving, glycaemic index and healthier-cooking tips. |
| 🕑 **History** | Past meals and today's calories. Stored on the device only. |

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

- **`shared/`**: the Ugandan/African food database (`foods.ts`), nutrition maths and health rules (`nutrition.ts`), and types. The app and the server use the same code, so the numbers always match.
- **`server/`**: the API. The Anthropic key stays here and is never shipped in the app.
- **`app/`**: the Expo Router app (`src/app/` holds the screens).
- **`ml/`**: data preparation, training and serving for Foodio's own food-image classifier (Kaggle African food data plus Ugandan photos). See [ml/README.md](ml/README.md).

The AI only identifies foods and estimates portions. Wherever a food matches the database, the calories and nutrients come from the database, and the rule-based health checks (e.g. "over 90 g carbs for a diabetic", "contains groundnuts") can override a softer AI verdict.

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
