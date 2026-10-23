package annotations;

import java.lang.annotation.*;
import org.springframework.stereotype.Component; // Import Spring

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component // Indique à Spring de créer un Bean pour cette classe !
public @interface Controllers {
}