#!/usr/bin/env python3
from PIL import Image, ImageDraw

# Criar imagem PNG do spinner
size = 32
image = Image.new('RGBA', (size, size), (0, 0, 0, 0))
draw = ImageDraw.Draw(image)

# Cor: Indigo (#6366F1)
color = (99, 102, 241, 255)

# Desenhar arco (3/4 de um círculo)
# bbox: [x0, y0, x1, y1]
bbox = [2, 2, size-2, size-2]
draw.arc(bbox, 0, 270, fill=color, width=3)

# Salvar
image.save('src/resources/images/spinner.png')
print("PNG spinner criado com sucesso!")
