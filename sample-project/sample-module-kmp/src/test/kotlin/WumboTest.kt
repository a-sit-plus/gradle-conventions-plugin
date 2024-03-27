import io.kotest.core.spec.style.FreeSpec
import io.kotest.property.Arb
import io.kotest.property.forAll
import io.kotest.property.arbitrary.string
import io.kotest.matchers.shouldBe

class WumboTest :FreeSpec( {
    "Wumbology" {
        forAll(Arb.string(0..100)){
            wumbo(it) == "Wumbo"
        }
    }
})