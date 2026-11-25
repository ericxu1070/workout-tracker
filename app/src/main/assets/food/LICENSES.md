# Food Classifier and Nutrition Data Licenses

This directory contains on-device machine learning models, label mappings, and nutritional data tables for the Plateline food recognition and calorie estimation feature.

---

## 1. Google AIY Vision Food Classifier (`model.tflite`)

* **Model Name:** Google AIY Vision Classifier Food V1 (`aiy/vision/classifier/food_V1`)
* **Publisher / Author:** Google LLC
* **Distribution Sources:**
  - Kaggle Models: `https://www.kaggle.com/models/google/aiy/tfLite/vision-classifier-food-v1`
  - TensorFlow Hub: `https://tfhub.dev/google/lite-model/aiy/vision/classifier/food_V1/1`
* **Architecture:** MobileNet V1 image classification network optimized for on-device inference
* **Input Tensor:** `[1, 192, 192, 3]` UINT8 (RGB pixel values 0–255)
* **Output Tensor:** `[1, 2024]` UINT8 (Softmax confidence scores for 2,024 classes)
* **License:** Apache License, Version 2.0

```text
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   Copyright Google LLC

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```

---

## 2. Food Classification Labels (`labels.txt`)

* **Source:** Google AIY Vision Kit Food V1 Label Map
* **Publisher / Author:** Google LLC
* **Content:** 2,024 food item and dish classification labels corresponding to the output tensor indices of `model.tflite`.
* **License:** Apache License, Version 2.0

---

## 3. Calorie Range Table (`calories.json`)

* **Source:** Written for this app. Each classifier label is assigned a rough per-serving calorie bucket by food category (21 distinct ranges across 1,989 labels; 1,419 labels fall back to 400–650 kcal).
* **Not derived from** USDA FoodData Central or any other published nutrient database. Treat every range as a coarse guess the user confirms or edits.
* **License:** Same as this repository.
* **Serving Methodology:** Each label maps to a lower and upper bound (`kcalLow` to `kcalHigh`), never a single exact number.
