package annotations;

import java.lang.annotation.*;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component; // Import Spring

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component // Indique à Spring de créer un Bean pour cette classe !
@Scope("singleton") // Sprint 11 : 1 contrôleur = 1 seule instance partagée
public @interface Controllers {
}