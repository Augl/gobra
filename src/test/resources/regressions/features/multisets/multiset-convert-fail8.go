package pkg

func foo(ghost xs seq[int]) (ghost m mset[bool]) {
  //:: ExpectedOutput(type_error)
  m = mset(xs)
}
