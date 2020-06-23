package pkg

func example1(ghost m mset[int]) {
  ghost n := mset(m)
  assert n == m
}

func example2(ghost m1 mset[int], ghost m2 mset[int]) {
  assert mset(m1 union m2) == mset(m1) union mset(m2)
  assert mset(m1 intersection m2) == mset(m1) intersection mset(m2)
  assert mset(m1 setminus m2) == mset(m1) setminus mset(m2)
}

func example3() {
  assert mset(mset[bool] { true, false, true }) == mset[bool] { true, false, true }
  assert mset(mset[bool] { }) == mset[bool] { }
  assert mset(mset[mset[int]] { mset[int] { 42 } }) == mset[mset[int]] { mset[int] { 42 } }
}

func example4(ghost x int, ghost m mset[int]) {
  assert x in mset(m) == x in m
}

ensures mset(mset(m)) == m
func example5(ghost m mset[int]) {
}
