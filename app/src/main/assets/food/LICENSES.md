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

## 3. Calorie & Nutrition Reference Table (`calories.json`)

* **Source:** U.S. Department of Agriculture (USDA) FoodData Central (FDC)
* **Databases:**
  - USDA Food and Nutrient Database for Dietary Studies (FNDDS) 2019–2020 & 2021–2023
  - USDA National Nutrient Database for Standard Reference (SR Legacy)
  - USDA Foundation Foods (Release October 2024)
* **URL:** `https://fdc.nal.usda.gov/`
* **License / Terms of Use:** U.S. Government Public Domain
  As a work of the United States Government prepared by officers and employees thereof as part of their official duties (Agricultural Research Service, USDA), this data is in the public domain in the United States and worldwide under 17 U.S.C. § 105. No copyright restrictions apply.
* **Serving Methodology:** Each food category maps to a typical single-meal serving portion with a lower and upper bound calorie estimate (`kcalLow` to `kcalHigh`), reflecting natural preparation and portion variances. In accordance with guidelines, calorie estimates are never presented as a single exact number.
