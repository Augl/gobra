package pkg

func foo(ghost m1 mset[bool], ghost m2 mset[int]) {
  //:: ExpectedOutput(type_error)
  ghost m := mset(m1 union m2)
}
