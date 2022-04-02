# rpqs-to-datalog
A Java library for converting RPQs (SPARQL property paths) to positive Datalog.

The code provides some optimisations to convert programs to linear recursion, push constants towards base predicated, perform inlining of redundany intermediary predicates, and prune tautological atoms and duplicate rules.

The code is developmental, not intended for production as-is. The project is structured as a classical Java project (dependencies hardcopied in `lib/`, source in `src/`). Pull requests welcome for mavenisation, etc.

## Input RPQs, output format and optimisations

The main class is `ConvertRPQsToDatalog`. It assumes as input a file with integer encoded RPQs on each line in SPARQL-like syntax of the form:

```
1 2/3* ?y .
?x ^4|5 ?y .
```

etc. It writes its output to a output directory, with a file containing a Datalog programme for each input RPQ.

The software was written to create Datalog programmes compatible with [LogicBlox](https://developer.logicblox.com/). We assume a graph in the form of a ternary predicate `E` of integers, and a unary `V` predicate for nodes. We will describe loading a graph into LogicBlox below in a manner compatible with the output programmes produced by this library. 

It can however be adapted to write out programmes in other syntaxes for other systems by changing the `toString()` methods and constants in the `Atom`, `Rule`, `GraphAtom` and `NoteAtom` classes.

In the `ConvertRPQsToDatalog`, the following code creates the base translation of RPQs to Datalog:

```
ArrayList<Rule> rules = opTransform((OpPath)op);
Program p = new Program(rules);
```

This is followed by a number of different optimisers that transform the program. Here you can enable to disable optimisations by commenting them out, chaining them in different orders, etc. 

This optimisers were designed with RPQs in mind, and were checked on around 2000 RPQs with respect to the number of results returned. They may or may not work for general Datalog programmes (not tested). 

## Loading graphs into LogicBlox

We assume a dictionary-encoded file `graph.dat` with comma-separated triples of integers of the form:

```
2,1,3
4,2,5
...
```

We assume you have acquired the code for LogicBlox and installed it. We've used LogicBloc v.4.40.0. The load was tested for a Wikidata graph of around 1 billion triples. The process of loading the graph into LogicBlox is as follows.

See if LogicBlox is up and running:

```
lb status
```

See what services are up

```
lb services status
```

If not running, start the services:

```
lb services stop
lb services start
```

Create graph workspace (user needs write permissions to workspace folder)

```
lb create graph
```

Navigate to folder with graph.dat file

```
cd /home/mygraph/
```

Open LogicBlox prompt

```
lb
```


Open graph workspace

```
open graph
```

Create ternary EDB predicate `E` for edges and IDB predicate `V` for vertices

```
addblock --name gschema 'E(s,p,o) -> int(s), int(p), int(o). lang:derivationType[`E] = "Extensional". V(n) <- E(n,_,_);E(_,_,n).'
```

Load edges from .dat file into predicate E and save load time in seconds to `loadtime.dat` in current directory.

```
exec --duration --duration-file load.dat '_in(offset;s,p,o) -> int(offset), int(s), int(p), int(o). lang:physical:filePath[`_in] = "graph.dat". lang:physical:delimiter[`_in] = " ". lang:physical:fileMode[`_in] = "import". +E(s,p,o) <- _in(_;s,p,o).'
```

Leave lb terminal.

```
exit
```

Request build of all index permutations that might be useful (POS, PSO, SPO, OPS, V). Since we work with RPQs, we assume constant predicates. Indexing time in seconds will be written to `index.dat`.

```
lb batch-script --duration --duration-file index.dat -t graph 'addIndex E/1_2_0 E/1_0_2 E/0_1_2 E/2_1_0 V/0'
```

Open lb terminal to test a query.

```
lb
```

You may need to change the query to ensure it returns results on your graph. Finds all subjects with predicate `1`.

```
query --duration --duration-file testq.dat '_(s) <- E(s,1,_).'
```

In order to run a query programme in a file `q.logic` as produced by this library (with a timeout), exit the lb terminal and try:

```
lb query --timeout 60000 --duration --duration-file q.dat --file q.logic wdtest
```

The time taken in seconds will be written to `q.dat`. Results will be streamed to standard out.
