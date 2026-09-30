package pulsoescolar_api.service.user;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationPhotoService {
    public byte[] read(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) return null;
        if (photo.getSize() > 2 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "A foto deve ter no máximo 2 MB.");
        }
        try (var stream = photo.getInputStream(); var imageInput = ImageIO.createImageInputStream(stream)) {
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw invalidPhoto();
            var reader = readers.next();
            try {
                reader.setInput(imageInput);
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) throw invalidPhoto();
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 4_000_000) throw invalidPhoto();
                var image = reader.read(0);
                var output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                if (output.size() > 2 * 1024 * 1024) throw invalidPhoto();
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw invalidPhoto();
        }
    }

    private ResponseStatusException invalidPhoto() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Envie uma foto JPEG ou PNG válida, com até 4 milhões de pixels e 2 MB.");
    }
}
