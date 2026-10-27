# Class Hierarchy

```
+------------------+
|     Animal       |
|  (abstract)      |
+------------------+
| - id : int       |
| - name : String  |
| - breed : String |
| - ageMonths : int|
| - intakeDate     |
| - status         |
+------------------+
| + species()      |
| + adoptionFee()  |
| + careSchedule() |
| + isSenior()     |
+------------------+
         |
    +----+----+
    |         |
+---v---+ +---v---+
|  Dog  | |  Cat  |
+-------+ +-------+
| +species()      |
| +adoptionFee()  |
| +careSchedule() |
| +isSenior()     |
+-------+ +-------+

+------------------+
| PetImageService  |
|  (interface)     |
+------------------+
| + providerName() |
| + supports()     |
| + fetchRandom... |
+------------------+
         |
    +----+----+
    |         |
+---v---+ +---v---+
|DogCeo | |CatApi |
|Image  | |Image  |
|Service| |Service|
+-------+ +-------+
```
