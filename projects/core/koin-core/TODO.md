Multibinding相关说明：
1. 以下描述，Multibinding均指代MapMultibinding和SetMultibinding
2. 核心逻辑：声明Multibinding时给定qualifier1，声明item时为item给定qualifier2，还会同时声明一个用于遍历该item的对象，给定qualifier3，并且该对象也保存了qualifier1和qualifier2
3. 对于MapMultibinding的使用有两种场景：
   1. 遍历，通过getAll获取所有qualifier3对象，过滤获取属于qualifier1的对象，然后通过qualifier2获取真正的item
   2. 指定key获取item，一般而言可以通过key和qualifier1直接计算出qualifier2，然后直接获取item
4. 对于SetMultibinding只有遍历一种场景
5. 单纯的获取Multibinding和获取其它普通对象一样，没有什么特别的，除非设置了createdAtStart
6. 可以看出，对于MapMultibinding遍历与不遍历有本质区别，虽然这在上层使用上完全看不出来
7. MultibindingTest展示了比较多的使用场景既一些边缘情况

Multibinding相关问题：
1. - [ ] 使用构造函数注入，非常容易出错，没有想到什么好的解决方案；尝试过在声明Multibinding指定它为默认的，等于记录下了此时的qualifier1，凡是获取Map/Set类型时就用这个Multibinding，但解决问题有限
   ```
   class A(val map: Map<K, V>)
   
   singleOf(::A) // ❌
   single {
     A(getMapMultibinding()) // ✅
   }
   
   // 更推荐的做法是动态注入
   class A : KoinComponent {
     private val map: Map<K, V> by injectMapMultibinding()
   }
   ```
2. - [ ] Multibinding的遍历性能是较差的，并且koin的instance是可以动态加载的，所以遍历的结果也不能缓存。现在的做法是，把qualifier2的item和qualifier3的对象都放在InstanceRegistry.internalInstances中，internalInstances里的对象较少，遍历效率较高，且可以缓存遍历结果，在internalInstances发生变化时再次遍历
   我认为这种做法比较可行，但是确实增加了instances维护的复杂度。此外，还有一个额外的好处，就是不能通过getAll的方式获取item，仿佛你定义的item就真的是在Multibinding内部一样。
3. - [ ] Multibinding的声明是幂等的，如果设置了allowOverride = false，多次声明Multibinding是否应该抛出异常，我认为不需要，只有多次声明相同item时才需要。如果不抛异常，可以通过TaggedInstanceFactory为Multibinding加上特定Tag实现