package pkg

func foo() {
  // fails since index 10 exceeds sequence length
  //:: ExpectedOutput(assignment_error:seq_index_exceeds_length_error)
  ghost xs := seq[1..10][2 = 17, 5 = 42, 10 = 100]
}
