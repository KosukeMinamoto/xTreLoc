@import "style.css"

# xTreLoc demo

Here is an example of xTreLoc using synthetic datasets.

## Required Files

To run xTreLoc, the following files are required:

- **config.json**: Parameter settings file.
- **station.tbl**: A space-separated file containing station latitude, longitude, elevation, and station correction values.
- **Velocity Structure Model (TauP[^1] format)**: Optional. The default TauP models can be used if not specified.
- **catalog.csv**: A CSV file storing hypocenter information. It can also serve as the ground truth for synthetic tests.

## Create synthetic datasets

Param. of `catalogFile` is referenced.

```sh
./xtreloc SYN
```

You can change purturbation params in the `SyntheticTest.java`.

- `phsErr`: Differential travel-time erros in seconds (default: 0.1)
- `locErr`: Hypocentral errors (in each axis) in degree (default: 0.03)
- The number of phases selected randomly (default: 20-40%)
    ```java
    int minPairs = (int)(numAllPairs * 0.2);
    int maxPairs = (int)(numAllPairs * 0.4); 
    ```

## Run location

```sh
./xtreloc STD
```

## View results

At first, summed results are obtained as below:
```sh
pyhton3 sumdat.py --dat_dir </path/to/dat-dir> --map_type <map_type>
```

You can choose `map_type` from gnuplot or pyhton or matlab or gmt6.

!!! Warning
    This program `sumdat.py` overwrites `catalog.list`.

You can see results easily using xTreLoc as:
```sh
./xtreloc SEE
```

<img src=see_ini.png width=250>
<img src=see_std.png width=250>

Or you can also use gnuplot as:

```sh {cmd}
gnuplot view.plt
```

<img src=gnu_ini.png width=250>
<img src=gnu_std.png width=250>

!!! warning Aspect ratio
    When drawing in Gnuplot mode, the aspect ratio is fixed at 1:1 which differs from the actual ratio, and similarly in xTreLoc SEE mode, the scale is **NOT** accurate.

Or, via Generic Mapping Tools (Ver.6) [^1]:

<img src=gmt_ini.png width=250>
<img src=gmt_std.png width=250>

## Notes

- In each mode, parameters specified in `config.json` as shown in the following table are loaded. "I" denotes input, and "O" denotes reference during output.

    | | GRS | STD | CLS | TRP | SYN | SEE |
    | :-- | :--: | :--: | :--: | :--: | :--: | :--: |
    | stnFile | I | I | I | I | I | I |
    | tauModFile | I | I | | I | I | |
    | catalogFile | | | | I/O | I | I |
    | datPattern | I | I | | | | I |
    | numJobs | I | I | | I | | | 
    | numGrid | I | | | | | |
    | hypBottom | I | I | | I | | |
    | threshold | I | I | I | I | | |
    | lsqr_* | | | | I | | |

- If using the STD method to determine shallower earthquakes (e.g., volcanic events) compared to stations, it is necessary to modify the program. This is because within `HypoLevenbergMarquardt.java`, earthquakes shallower than any of the observation points are considered "Airquake". Specifically, the following needs to be commented out:
	```java
	.parameterValidator(new ParameterValidator() {
		@Override
		public RealVector validate(RealVector params) {
			if (params.getEntry(2) <= stnBottom){ // Airquake
				params.setEntry(2, Math.random()*hypBottom);
			}
			return params;
		}
	})
	```

- (Although verification on this issue is insufficient,) when using a custom TauP model, care should be taken not to place too many velocity contrasts in the shallow region (~20 km?). If using a non-original model, it is recommended to check whether synthetic tests can be reproduced.

## Additional Tools

Several auxiliary programs are included in the `test` directory:

- sumdat.py (Python script): 
	This script reads all files in `dat_dir` and generates a visualization script in the specified format (default: gnuplot).

	Usage:
	```sh
	python3 sumdat.py --dat_dir DAT_DIR [--map_type {gnuplot,python,matlab,gmt6}]
	```


[^1]: Wessel et al. (2019)