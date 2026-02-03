@echo off
echo Reconectando unidades de rede...

REM Mapeie as unidades de rede necessárias
REM Substitua as letras de unidade e os caminhos pelos seus próprios

REM Exemplo:
REM net use X: \\servidor\compartilhamento1 /persistent:yes
REM net use Y: \\servidor\compartilhamento2 /persistent:yes

REM Reconecte as unidades de rede
net use * /delete /yes
net use G: \\10.254.1.236\Grupo /persistent:yes
net use O: \\10.254.1.236\Publico /persistent:yes
net use P: \\10.254.1.236\Publica /persistent:yes

echo Unidades de rede reconectadas com sucesso!