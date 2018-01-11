package uk.ac.wellcome.transformer.source

case class SierraItemData(
  id: String,
  deleted: Boolean,
  fixedFields: Map[String, FixedField] = Map(),
  varFields: List[VarField] = List()
)
