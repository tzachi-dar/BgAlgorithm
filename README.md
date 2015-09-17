## BgAlgorithm
A program that is used to evaluate algorithms that create bg from raw readings, this takes xDrip Database exports as input.


#### Currently the algorithms in test are:
* `LineFitAlgorithm`
* `xDripAlgoritm`

###### _note that the xDrip algorithm just uses the values currently in the db and is not recalculating anything._


#### Adding a new algorithm?
* Add it to `SQLiteJbdc.java`
* Be sure to implement `BgAlgorithm`

#### Running it
##### On a Mac/Linux
* after any changes run `./compile.sh`
* run with `./run/sh path/to/db.sqlite AlgorithmName`
  * since Im lazy I always run something like `./compile.sh; ./run.sh db2.sqlite xDripAlgorithm`
