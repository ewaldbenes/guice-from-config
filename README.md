# Google Guice Together with Configuration

Guice is an awesome Java library for dependency injection. It's lightweight, well engineered, battle tested
by Google, it has extensive documentation and is relatively easy to use once you've grasped the concept behind
inversion of control and DI.

When I started to employ Guice on an existing project around seven years ago I accidentally hit a use case
that Guice seemed to not have covered. I faced tons of configuration files which described how many
instances each class needed, they described the dependency hierarchy which instance holds another
instance or a set of instances. I'm not talking about configuration that only sets some primitive
values inside a class differently but really influences on how the dependency graph looks like.

Config files looked something like this in JSON:

```
[
    {
        "id": 1,,
        ...
        "children": [
            {
                "id": "A",
                "someProp": "abc",
                ...
            },
            {
                "id": "B",
                "someProp": "efg",
                 ...
            }
        ]
    },
    {
        "id": 2,
        ...,
        "children": [
            {
                "id": "C",
                "someProp": "xyz",
                ...
            },
            {
                "id": "D",
                "someProp": "spq",
                ...
            }
        ]
    }
]
```

This config file should instruct Guice to create a dependency graph to look like following:

```
              Set<Parent>
             /          \
     Parent 1            Parent 2
        |                   |
    Set<Child>          Set<Child>
     /    \              /    \
Child A  Child B    Child C  Child D
```

Now you might ask why the hell do I need something like this? Well, that's because it's a customizable piece of
warehouse automation software that needs to be adapted for every client. Each client has a different amount
of installations (although they are standard hence same class) and this information comes from configuration files.

Everywhere in the official Guice docs it's stated that either each instance A gets a brand-new instance of B or
there are only real singletons of each class lying around in production code. Clearly not my case!
I had many "singletons" of the same class with different properties doing their job. You can think of it as
having an army of helicopters (same brand aka class) each equipped with a different pilot (fields of the class).

To solve the problem I ended up creating tons of provider methods effectively instantiating most or part of
of the dependency tree myself which defeats the whole purpose of Guice.

An example where we assume that we have set of class A and each holds a set of instances of class B
(B is the leaf in the dependency tree for simplicity) and the description of which instance goes into which
is given in the configuration file (here already an object again for simplicity).

```
class SomeModule extends AbstractModule {
    ...
    @Provider
    @Singleton
    Set<A> createAs(Config config, Set<B> setOfAllBs) {
        HashSet<A> setOfAs = new HashSet<>();
        for(AConfig ac : config.as()) {
            // here we need to find from setOfAllBs which instances go into A
            // I ommit this piece of code for brevity
            // setOfAllBs.stream().find(...) and collect to set
            // B needs to expose the values from the configuration to find the mapping
            setOfAs.add(new A(ac.a, ac.b, b);
        }
        return setOfAs;
    }

    @Provider
    @Singleton
    Set<B> createAllBs(Config config) {
        HashSet<B> setOfBs = new HashSet<>();
        for(AConfig ac: config.as()) {
            for(BConfig bc: ac.bs()) {
                // B is the leaf dependency which doesn't need any other injected instances
                setOfBs.add(new B(bc.a, bc.b,...));
            }
        }
        return setOfBs;
    }
    ...
}
```

As you can see this is definitely not Guicey nor automatic dependency injection but doing it all manually.
I would have been better of not using Guice at all. Furthermore if the dependency graph is huge you end up
having dozens of providers, iterating endless times through the same collections reconstructing the graph from
flat collections. Believe me! It's horrible mess. Constructing the dependency graph is actually the job of Guice
(that's what it's made for) and not ours.

**There must be a better way and let Guice do all the work. And there is!**

## Guice Keys, Multibindings and Private Modules to Rescue

Here the same example as before but done the Guice way via automatic injections defined via keys and private modules.


```
class Config implements Module {

    Set<AConfig> setOfAConfigs = new HashSet<>();

    @Override public void configure(Binder binder) {
        Multibinder<A> aMultiBinder = Multibinder.newSetBinder(binder, A.class);
        for(AConfig a : setOfAConfigs) {
            PrivateBinder aBinder = binder.newPrivateBinder();
            Key<A> aKey = Key.get(A.class, new RealAAnnot(a.num));
            aBinder.bind(aKey).to(A.class).in(Singleton.class);
            aBinder.expose(aKey);
            aMultiBinder.addBinding().to(aKey);

            Multibinder<B> bMultiBinder = Multibinder.newSetBinder(aBinder, B.class);
            for (BConfig b : a.setOfBConfigs) {
                PrivateBinder bBinder = aBinder.newPrivateBinder();
                bBinder.bind(BigInteger.class).toInstance(BigInteger.valueOf(a.num));
                bBinder.bind(Integer.class).toInstance(b.num);
                Key<B> bKey = Key.get(B.class, new RealBAnnot(a.num, b.num));
                bBinder.bind(bKey).to(B.class).in(Singleton.class);
                bBinder.expose(bKey);
                aBinder.expose(bKey); // makes it in top-level injector (globally) available
                bMultiBinder.addBinding().to(bKey);
            }
        }
    }
}
```

I have to admit that the code is a little longer now and doesn't look quite "clean". Java is also quite verbose
and Guice's fluent API brings that up even more. But there's a clear advantage:

**It scales and it scales really well for big apps!**

The core concept is to divide the dependency graph into subgraphs via private modules (child injectors under the hood).
Each instance managed by Guice is uniquely identified via a `Key` that is composed of the class' type and a
custom annotation instance that holds values from the configuration (important to override `hashCode` and
`equals` methods correctly). So each instance is connected to its declaration inside the configuration.
Guice happily takes care of resolving the dependency graph.

*The `GuiceFromConfigurationTest` file in the `test` source set contains an even more elaborate example
and extensive Javadoc. The source of the annotations is inside the `main` source set.*

