package pulsoescolar_api.service.user;

import pulsoescolar_api.exception.InvalidRegistrationPhotoException;
import pulsoescolar_api.exception.RegistrationPhotoTooLargeException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RegistrationPhotoService {
    public byte[] read(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) return null;
        if (photo.getSize() > 2 * 1024 * 1024) {
            throw new RegistrationPhotoTooLargeException();
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

    private InvalidRegistrationPhotoException invalidPhoto() {
        return new InvalidRegistrationPhotoException();
    }
}
