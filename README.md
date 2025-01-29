# xTreLoc

## About this program

This is the Cross-correlation based Tremor reLocation tool (xTreLoc) developed by K.M.

This software supports:
- Hypocenter location based on the differential travel-time of the S-wave
- Hypocenter relocation using the relative location method
- Check located results with ease

## Compile

### Maven

```
mvn clean package
```

* mavenで依存関係まるごとダウンロード: `mvn dependency:copy-dependencies -DoutputDirectory=lib`

### Gradle

```
gradle clean build
```

## Usage

```sh {cmd=True}
./xtreloc
```


## Configure file

### Velocity model

- カスタム速度構造
\
`taup velmerge -mod prem --nd merge aob.nd`
\
`taup create -nd prem_aob.nd`
\
For more info., see taup manual (p.15)

### Station table

Space(s) separated station table with the columns of station code (String), latidude (float), longitude (float), depth (float, pos down in km), travel-time correction val. (float, P-wave), the same as former (float, S-wave), user defined vals. (Not recognized)

Here is the example of Ocean Bottom Seismometers (OBSs) deployed in the Japan Trench (Around North 39 deg., East 143 deg., 2 km below the sea-surface).

@import "station_B.tbl" {line_begin=2 line_end=5}

<!-- {code_block=true class="line-numbers"} -->

### datPattern

The pattern of the files in the UNIX format, ***NOT in the java format***.

## Example

### Synthetic Test

At first, you need to prepare true catalog like this:

@import "synthetic.csv" {line_end=4}

Next, generate datasets for synthetic test as below:

```
java -jar build/libs/xtreloc-1.0-SNAPSHOT.jar SYN
```

Note that columns of date, error, type are NOT used here, and example datasets will be written in the path. Location with the Station-pair DD method can be done as:

```
java -jar build/libs/xtreloc-1.0-SNAPSHOT.jar STD
```

and also with the Triple Difference method as:

```
java -jar build/libs/xtreloc-1.0-SNAPSHOT.jar TDR
```

!!! warning
    Files in the "dat-out" directory will be overwritten after running (re-) location. You must copy the directory in each steps if you want.

## Utilities

- sumdat\.py

- map.plt


## License

```txt
Copyright 2025 Kosuke Minamoto

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
