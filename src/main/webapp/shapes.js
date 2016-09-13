// By Simon Sarris
// www.simonsarris.com
// sarris@acm.org
//
// Last update December 2011
//
// Free to use and distribute at will
// So long as you are nice to people, etc

// dashed line functionality
CanvasRenderingContext2D.prototype.dashedLine = function(x1, y1, x2, y2, dashLen)
{
    if (dashLen == undefined)
        dashLen = 2;
    this.moveTo(x1, y1);

    var dX = x2 - x1;
    var dY = y2 - y1;
    var dashes = Math.floor(Math.sqrt(dX * dX + dY * dY) / dashLen);
    var dashX = dX / dashes;
    var dashY = dY / dashes;

    var q = 0;
    while (q++ < dashes)
    {
        x1 += dashX;
        y1 += dashY;
        this[q % 2 == 0 ? 'moveTo' : 'lineTo'](x1, y1);
    }
    this[q % 2 == 0 ? 'moveTo' : 'lineTo'](x2, y2);
};

function SelectionBox(x, y, w, h)
{
    this.x = x || 0;
    this.y = y || 0;
    this.w = w || 1;
    this.h = h || 1;
}

// Constructor for Shape objects to hold data for all drawn objects.
// For now they will just be defined as rectangles.
function Shape(x, y, w, h, id)
{
    // This is a very simple and unsafe constructor. All we're doing is checking
    // if the values exist.
    // "x || 0" just means "if there is a value for x, use that. Otherwise use
    // 0."
    // But we aren't checking anything else! We could put "Lalala" for the value
    // of x
    this.x = x || 0;
    this.y = y || 0;
    this.w = w || 1;
    this.h = h || 1;
    this.id = id;
    this.selectionHandles = [];
    this.selBoxSize = 6;

    for ( var i = 0; i < 8; ++i)
    {
        this.selectionHandles.push(new SelectionBox(0, 0, this.selBoxSize, this.selBoxSize));
    }

    this.updateBoxes();
}

Shape.prototype.updateBoxes = function()
{
    var half = this.selBoxSize / 2;
    // 0 1 2
    // 3   4
    // 5 6 7

    for (var i = 0; i < 8; ++i)
    {
        var handle = this.selectionHandles[i];
        handle.x = this.x - half;
        handle.y = this.y - half;
        
        var idx = i + Math.floor(i/4);// makes it easier to find positions
        
        if (idx % 3 > 0)
        {
            if (idx%3 == 1) handle.x += this.w/2;
            else handle.x += this.w;
        }
        if (idx >= 3)
        {
            if (idx < 6) handle.y += this.h/2;
            else handle.y += this.h;
        }
    }
};

// Draws this shape to a given context
Shape.prototype.draw = function(ctx)
{
    ctx.fillStyle = 'rgb(255, 0, 0)';
    for ( var i = 0; i < 8; i++)
    {
        var cur = this.selectionHandles[i];
        ctx.fillRect(cur.x, cur.y, cur.w, cur.h);
    }
    ctx.strokeStyle = 'rgb(255, 0, 0)';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.dashedLine(this.x, this.y, this.x + this.w, this.y, 5);
    ctx.dashedLine(this.x + this.w, this.y, this.x + this.w, this.y + this.h, 5);
    ctx.dashedLine(this.x + this.w, this.y + this.h, this.x, this.y + this.h, 5);
    ctx.dashedLine(this.x, this.y + this.h, this.x, this.y, 5);
    ctx.stroke();
    ctx.closePath();
};

// Determine if a point is inside the shape's bounds
Shape.prototype.contains = function(mx, my)
{
    return (this.x <= mx) && (this.x + this.w >= mx) && (this.y <= my) && (this.y + this.h >= my);
};

function CanvasState(canvas)
{
    // **** First some setup! ****

    this.canvas = canvas;
    // Some pages have fixed-position bars (like the stumbleupon bar) at the top
    // or left of the page
    // They will mess up mouse coordinates and this fixes that
    var html = document.body.parentNode;
    this.htmlTop = html.offsetTop;
    this.htmlLeft = html.offsetLeft;

    // **** Keep track of state! ****

    this.valid = false; // when set to false, the canvas will redraw everything
    this.shapes = []; // the collection of things to be drawn
    this.selection = -1;
    this.dragoffx = 0; // See mousedown and mousemove events for explanation
    this.dragoffy = 0;
    this.expectResize = -1;
    this.page = null;

    // **** Then events! ****

    // This is an example of a closure!
    // Right here "this" means the CanvasState. But we are making events on the
    // Canvas itself,
    // and when the events are fired on the canvas the variable "this" is going
    // to mean the canvas!
    // Since we still want to use this particular CanvasState in the events we
    // have to save a reference to it.
    // This is our reference!
    var myState = this;

    // fixes a problem where double clicking causes text to get selected on the
    // canvas
    canvas.addEventListener('selectstart', function(e)
    {
        e.preventDefault();
        return false;
    }, false);
    canvas.addEventListener('contextmenu', function(e)
    {
        e.preventDefault();
    });
    // Up, down, and move are for dragging
    canvas.addEventListener('mousedown', function(e)
    {
        e.preventDefault();
        // we are over a selection box
        if (e.button == 0 && myState.expectResize !== -1)
        {
            myState.resizeDrag = true;
            myState.valid = false;
        }
        else if (e.button == 2 && myState.selection >= 0 && myState.selection != myState.shapes.length-1)
        {
            var shape = myState.removeShapeAt(myState.selection);
            myState.addShape(shape);
            myState.page.movePanel(myState.selection, myState.shapes.length-1);
            myState.selection = myState.shapes.length-1;
        }
    }, true);
    canvas.addEventListener('mousemove', function(e)
    {
        if (myState.resizeDrag)
        {
            var mouse = myState.getMouse(e);
            // time ro resize!
            var mySel = myState.shapes[myState.selection];
            var oldx = mySel.x;
            var oldy = mySel.y;
            var mx = mouse.x;
            var my = mouse.y;

            // 0 1 2
            // 3   4
            // 5 6 7
            var idx = myState.expectResize + Math.floor(myState.expectResize/4);
            if (idx % 3 == 0) mySel.x = mx;
            if (idx < 3)      mySel.y = my;
            
            if (idx % 3 == 0) mySel.w += oldx - mx;
            if (idx % 3 == 2) mySel.w = mx - oldx;
            
            if (idx < 3)  mySel.h += oldy - my;
            if (idx >= 6) mySel.h = my - oldy;
            mySel.updateBoxes();
            myState.valid = false;
        }
        else
        {
            var mouse = myState.getMouse(e);
            var oldSel = myState.selection;
            myState.selection = -1;
            for ( var b = 0; b < myState.shapes.length; ++b)
            {
                var shape = myState.shapes[b];
                var sel = -1;
                
                if (shape.contains(mouse.x, mouse.y))
                {
                    if (sel == -1 || myState.shapes[sel].w*myState.shapes[sel].h > shape.w*shape.h)
                    {
                        sel = b;
                    }
                }
                if (sel >= 0)
                    myState.selection = sel;
                
                for ( var i = 0; i < 8; i++)
                {
                    // 0 1 2
                    // 3   4
                    // 5 6 7

                    var cur = shape.selectionHandles[i];

                    if (mouse.x >= cur.x && mouse.x <= cur.x + shape.selBoxSize && mouse.y >= cur.y && mouse.y <= cur.y + shape.selBoxSize)
                    {
                        // we found one!
                        myState.expectResize = i;
                        myState.selection = b;
                        var cursors = ['nw-resize', 'n-resize', 'ne-resize', 'w-resize', 'e-resize', 'sw-resize', 's-resize', 'se-resize'];
                        this.style.cursor = cursors[i];
                        myState.valid = false;
                        return;
                    }
                }
            }
            //myState.valid = (oldSel == myState.selection); // don't do this, puts the variable back to true when it still needs to be drawn
            if (oldSel != myState.selection)
                myState.valid = false;
            // not over a selection box, return to normal
            myState.resizeDrag = false;
            myState.expectResize = -1;
            this.style.cursor = 'auto';
        }
    }, true);
    canvas.addEventListener('mouseup', function(e)
    {
        if (myState.resizeDrag)
        {
            myState.resizeDrag = false;
            myState.valid = false;
            var shape = myState.shapes[myState.selection];
            myState.page.panels[myState.selection].resize(shape.x, shape.y, shape.w, shape.h);
        }
    }, true);
    canvas.addEventListener('mouseout', function(e)
    {
        if (myState.resizeDrag)
        {
            myState.resizeDrag = false;
            myState.valid = false;
        }
    });
    // double click for making new shapes
    canvas.addEventListener('dblclick',function(e)
    {
        var mouse = myState.getMouse(e);
        // https://developer.mozilla.org/en-US/docs/Web/API/MouseEvent
        if (e.altKey)
        {
            if (myState.selection >= 0)
            {
                myState.page.panels[myState.selection].destroy();
                myState.removeShapeAt(myState.selection);
                myState.selection = -1;
            }
        }
        else if (!e.ctrlKey)
        {
            var shape = new Shape(mouse.x - 20, mouse.y - 20, 40, 40);
            myState.addShape(shape);
            myState.selection = myState.shapes.length - 1;
            myState.page.addPanelByValues(shape.x, shape.y, shape.w, shape.h);
        }
        myState.valid = false;
    }, true);

    // **** Options! ****
    this.interval = 30;
    setInterval(function()
    {
        myState.draw();
    }, myState.interval);
}

CanvasState.prototype.addShape = function(shape)
{
    this.shapes.push(shape);
    this.valid = false;
};

CanvasState.prototype.removeShapeAt = function(idx)
{
    var shape = this.shapes.splice(idx, 1)[0];
    this.valid = false;
    return shape;
};

CanvasState.prototype.clearShapes = function()
{
    this.shapes = [];
    this.selection = -1;
    this.valid = false;
};

CanvasState.prototype.clear = function()
{
    this.canvas.getContext('2d').clearRect(0, 0, this.canvas.width, this.canvas.height);
};

// While draw is called as often as the INTERVAL variable demands,
// It only ever does something if the canvas gets invalidated by our code
CanvasState.prototype.draw = function()
{
    // if our state is invalid, redraw and validate!
    if (!this.valid)
    {
        this.valid = true; // do this first so there are no race conditions for this variable
        
        var ctx = this.canvas.getContext('2d');
        var shapes = this.shapes;
        this.clear();

        // mask
        var mask = document.createElement('canvas');
        mask.width = this.canvas.width;
        mask.height = this.canvas.height;
        var maskCtx = mask.getContext('2d');

        // draw all shapes
        // https://developer.mozilla.org/samples/canvas-tutorial/6_1_canvas_composite.html
        maskCtx.fillStyle = 'rgb(0, 0, 0)';
        for ( var i = 0; i < shapes.length; i++)
        {
            maskCtx.fillRect(shapes[i].x, shapes[i].y, shapes[i].w, shapes[i].h);
        }        
        ctx.globalAlpha = 0.3;
        ctx.drawImage(mask, 0, 0);
        
        // draw dark border on unused parts
        maskCtx.globalCompositeOperation = 'source-over';
        maskCtx.fillStyle = 'rgb(0, 0, 0)';
        maskCtx.fillRect(0, 0, mask.width, mask.height);
        maskCtx.fillStyle = 'rgb(255, 255, 255)';
        for ( var i = 0; i < shapes.length; i++)
        {
            maskCtx.clearRect(shapes[i].x, shapes[i].y, shapes[i].w, shapes[i].h);
        }
        ctx.globalAlpha = 0.7;
        ctx.drawImage(mask, 0, 0);

        ctx.globalAlpha = 1;
        // clear selected rectangle
        if (this.selection >= 0)
        {
            ctx.globalCompositeOperation = 'destination-out';
            ctx.fillStyle = 'rgb(255, 255, 255)';
            ctx.fillRect(this.shapes[this.selection].x, this.shapes[this.selection].y, this.shapes[this.selection].w, this.shapes[this.selection].h);
        }
        
        // draw selection borders and text
        ctx.globalCompositeOperation = 'source-over';
        ctx.font = 'bold 20pt Verdana';
        for ( var i = 0; i < shapes.length; i++)
        {
            if (!this.resizeDrag || i != this.selection)
            {
                shapes[i].draw(ctx);
                
                // idx
                ctx.fillStyle = 'rgb(255, 255, 255)';
                ctx.strokeStyle = 'rgb(0, 0, 0)';
                ctx.fillText(i+1, shapes[i].x + 8, shapes[i].y + 30);
                ctx.strokeText(i+1, shapes[i].x + 8, shapes[i].y + 30);
            }
        }

    }
};

// Creates an object with x and y defined, set to the mouse position relative to
// the state's canvas
// If you wanna be super-correct this can be tricky, we have to worry about
// padding and borders
CanvasState.prototype.getMouse = function(e)
{
    var element = this.canvas, offsetX = 0, offsetY = 0, mx, my;

    // Compute the total offset
    if (element.offsetParent !== undefined)
    {
        do
        {
            offsetX += element.offsetLeft;
            offsetY += element.offsetTop;
        }
        while ((element = element.offsetParent));
    }

    // Add padding and border style widths to offset
    // Also add the <html> offsets in case there's a position:fixed bar
    offsetX += this.htmlLeft;
    offsetY += this.htmlTop;

    mx = e.pageX - offsetX;
    my = e.pageY - offsetY;

    // We return a simple javascript object (a hash) with x and y defined
    return {x : mx, y : my};
};